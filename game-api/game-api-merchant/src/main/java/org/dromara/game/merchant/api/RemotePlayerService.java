package org.dromara.game.merchant.api;

import org.dromara.game.merchant.api.domain.vo.RemotePlayerVo;

/**
 * 玩家远程服务
 *
 * @author MLChong
 */
public interface RemotePlayerService {

    /**
     * 查询或自动建档玩家（launch 链路调用，并发安全）
     *
     * @param merchantId       商户ID
     * @param externalPlayerId 商户侧玩家标识
     * @return 玩家信息
     */
    RemotePlayerVo getOrCreate(Long merchantId, String externalPlayerId);
}
