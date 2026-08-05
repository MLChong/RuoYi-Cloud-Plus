package org.dromara.game.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.game.merchant.domain.MerchantPlayer;
import org.dromara.game.merchant.mapper.MerchantPlayerMapper;
import org.dromara.game.merchant.service.impl.PlayerServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("dev")
@DisplayName("玩家档案服务")
@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private MerchantPlayerMapper baseMapper;

    @InjectMocks
    private PlayerServiceImpl service;

    private MerchantPlayer player(String status) {
        MerchantPlayer p = new MerchantPlayer();
        p.setPlayerId(100L);
        p.setMerchantId(1L);
        p.setExternalPlayerId("u001");
        p.setStatus(status);
        return p;
    }

    @Test
    @DisplayName("已存在则直接返回，不插入")
    void getOrCreate_existing_returns() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(player("0"));

        MerchantPlayer result = service.getOrCreate(1L, "u001");

        assertEquals(100L, result.getPlayerId());
        verify(baseMapper, never()).insert(any(MerchantPlayer.class));
    }

    @Test
    @DisplayName("不存在则自动建档")
    void getOrCreate_new_inserts() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(baseMapper.insert(any(MerchantPlayer.class))).thenReturn(1);

        MerchantPlayer result = service.getOrCreate(1L, "u001");

        assertEquals("u001", result.getExternalPlayerId());
        assertEquals("0", result.getStatus());
        verify(baseMapper).insert(any(MerchantPlayer.class));
    }

    @Test
    @DisplayName("并发撞唯一键：捕获DuplicateKey后重查返回")
    void getOrCreate_race_reselects() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(null)
            .thenReturn(player("0"));
        when(baseMapper.insert(any(MerchantPlayer.class)))
            .thenThrow(new DuplicateKeyException("uk_merchant_external"));

        MerchantPlayer result = service.getOrCreate(1L, "u001");

        assertEquals(100L, result.getPlayerId());
        verify(baseMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("玩家已停用则抛异常")
    void getOrCreate_disabled_throws() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(player("1"));
        assertThrows(ServiceException.class, () -> service.getOrCreate(1L, "u001"));
    }
}
