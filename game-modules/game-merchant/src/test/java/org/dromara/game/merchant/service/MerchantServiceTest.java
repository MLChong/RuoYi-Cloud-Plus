package org.dromara.game.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.game.merchant.domain.Merchant;
import org.dromara.game.merchant.domain.bo.MerchantBo;
import org.dromara.game.merchant.mapper.MerchantMapper;
import org.dromara.game.merchant.service.impl.MerchantServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("dev")
@DisplayName("商户服务")
@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantMapper baseMapper;

    @InjectMocks
    private MerchantServiceImpl service;

    private MerchantBo validBo() {
        MerchantBo bo = new MerchantBo();
        bo.setMerchantCode("mch_test01");
        bo.setMerchantName("测试商户");
        bo.setWalletMode("TRANSFER");
        bo.setCurrencies("CNY,USDT");
        bo.setFeeRate(new BigDecimal("0.08"));
        return bo;
    }

    @Test
    @DisplayName("新增：编码已存在则抛异常")
    void insert_duplicateCode_throws() {
        when(baseMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);
        assertThrows(ServiceException.class, () -> service.insertMerchant(validBo()));
        verify(baseMapper, never()).insert(any(Merchant.class));
    }

    @Test
    @DisplayName("新增：自动生成64位密钥、默认状态0、额度0")
    void insert_generatesSecretAndDefaults() {
        when(baseMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
        when(baseMapper.insert(any(Merchant.class))).thenReturn(1);

        int rows = service.insertMerchant(validBo());

        assertEquals(1, rows);
        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(baseMapper).insert(captor.capture());
        Merchant saved = captor.getValue();
        assertTrue(saved.getSecret().matches("^[0-9a-f]{64}$"));
        assertEquals("0", saved.getStatus());
        assertEquals(BigDecimal.ZERO, saved.getPrepaidBalance());
    }

    @Test
    @DisplayName("修改：不允许变更编码与密钥")
    void update_neverTouchesCodeAndSecret() {
        MerchantBo bo = validBo();
        bo.setMerchantId(1L);
        when(baseMapper.updateById(any(Merchant.class))).thenReturn(1);

        service.updateMerchant(bo);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(baseMapper).updateById(captor.capture());
        assertNull(captor.getValue().getMerchantCode());
        assertNull(captor.getValue().getSecret());
    }

    @Test
    @DisplayName("重置密钥：返回新64位密钥")
    void resetSecret_returnsNewSecret() {
        Merchant db = new Merchant();
        db.setMerchantId(1L);
        db.setSecret("old");
        when(baseMapper.selectById(1L)).thenReturn(db);
        when(baseMapper.updateById(any(Merchant.class))).thenReturn(1);

        String secret = service.resetSecret(1L);

        assertTrue(secret.matches("^[0-9a-f]{64}$"));
    }

    @Test
    @DisplayName("重置密钥：商户不存在抛异常")
    void resetSecret_notFound_throws() {
        when(baseMapper.selectById(9L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.resetSecret(9L));
    }

    @Test
    @DisplayName("额度调整：余额不足（原子守卫更新0行）抛异常")
    void adjustPrepaid_insufficient_throws() {
        when(baseMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertThrows(ServiceException.class,
            () -> service.adjustPrepaid(1L, new BigDecimal("-100")));
    }

    @Test
    @DisplayName("额度调整：成功")
    void adjustPrepaid_ok() {
        when(baseMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        assertDoesNotThrow(() -> service.adjustPrepaid(1L, new BigDecimal("100")));
    }

    @Test
    @DisplayName("额度调整：delta为0直接拒绝")
    void adjustPrepaid_zero_throws() {
        assertThrows(ServiceException.class,
            () -> service.adjustPrepaid(1L, BigDecimal.ZERO));
        verifyNoInteractions(baseMapper);
    }
}
