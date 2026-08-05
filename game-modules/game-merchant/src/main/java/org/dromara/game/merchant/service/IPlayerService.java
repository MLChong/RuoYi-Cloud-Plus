package org.dromara.game.merchant.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.game.merchant.domain.MerchantPlayer;
import org.dromara.game.merchant.domain.vo.PlayerVo;

/**
 * 玩家档案服务层
 *
 * @author MLChong
 */
public interface IPlayerService {

    /**
     * 查询或自动建档（launch 链路，并发安全）；玩家停用抛 ServiceException
     */
    MerchantPlayer getOrCreate(Long merchantId, String externalPlayerId);

    /**
     * 后台分页查询
     */
    TableDataInfo<PlayerVo> queryPageList(Long merchantId, String externalPlayerId, PageQuery pageQuery);

    /**
     * 启用/停用玩家
     */
    int changeStatus(Long playerId, String status);
}
