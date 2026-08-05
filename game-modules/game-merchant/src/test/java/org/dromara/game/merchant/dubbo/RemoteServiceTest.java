package org.dromara.game.merchant.dubbo;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.game.merchant.api.domain.vo.RemoteMerchantVo;
import org.dromara.game.merchant.api.domain.vo.RemotePlayerVo;
import org.dromara.game.merchant.domain.Merchant;
import org.dromara.game.merchant.domain.MerchantPlayer;
import org.dromara.game.merchant.service.IMerchantService;
import org.dromara.game.merchant.service.IPlayerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
@DisplayName("Dubbo 远程服务")
@ExtendWith(MockitoExtension.class)
class RemoteServiceTest {

    @Mock
    private IMerchantService merchantService;

    @Mock
    private IPlayerService playerService;

    @InjectMocks
    private RemoteMerchantServiceImpl remoteMerchantService;

    @InjectMocks
    private RemotePlayerServiceImpl remotePlayerService;

    private Merchant merchant(String status) {
        Merchant m = new Merchant();
        m.setMerchantId(1L);
        m.setMerchantCode("mch_test01");
        m.setMerchantName("测试商户");
        m.setStatus(status);
        m.setSecret("a".repeat(64));
        m.setWalletMode("TRANSFER");
        m.setCurrencies("CNY,USDT");
        m.setFeeRate(new BigDecimal("0.08"));
        m.setPrepaidBalance(new BigDecimal("1000"));
        return m;
    }

    @Test
    @DisplayName("getByCode：币种串转列表，字段完整映射")
    void getByCode_mapsFields() {
        when(merchantService.getByCode("mch_test01")).thenReturn(merchant("0"));

        RemoteMerchantVo vo = remoteMerchantService.getByCode("mch_test01");

        assertEquals(1L, vo.getMerchantId());
        assertEquals(List.of("CNY", "USDT"), vo.getCurrencies());
        assertEquals("a".repeat(64), vo.getSecret());
    }

    @Test
    @DisplayName("getByCode：不存在返回null")
    void getByCode_notFound_returnsNull() {
        when(merchantService.getByCode("nope")).thenReturn(null);
        assertNull(remoteMerchantService.getByCode("nope"));
    }

    @Test
    @DisplayName("getOrCreate：商户不存在或已停用则抛异常，不建档")
    void getOrCreate_merchantMissingOrDisabled_throws() {
        when(merchantService.queryById(1L)).thenReturn(null);
        assertThrows(ServiceException.class,
            () -> remotePlayerService.getOrCreate(1L, "u001"));
        verifyNoInteractions(playerService);
    }

    @Test
    @DisplayName("getOrCreate：正常链路返回玩家")
    void getOrCreate_ok() {
        org.dromara.game.merchant.domain.vo.MerchantVo mvo = new org.dromara.game.merchant.domain.vo.MerchantVo();
        mvo.setMerchantId(1L);
        mvo.setStatus("0");
        when(merchantService.queryById(1L)).thenReturn(mvo);
        MerchantPlayer p = new MerchantPlayer();
        p.setPlayerId(100L);
        p.setMerchantId(1L);
        p.setExternalPlayerId("u001");
        p.setStatus("0");
        when(playerService.getOrCreate(1L, "u001")).thenReturn(p);

        RemotePlayerVo vo = remotePlayerService.getOrCreate(1L, "u001");

        assertEquals(100L, vo.getPlayerId());
        assertEquals("u001", vo.getExternalPlayerId());
    }
}
