package org.dromara.game.merchant.dubbo;

import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.game.merchant.api.RemotePlayerService;
import org.dromara.game.merchant.api.domain.vo.RemotePlayerVo;
import org.dromara.game.merchant.domain.MerchantPlayer;
import org.dromara.game.merchant.domain.vo.MerchantVo;
import org.dromara.game.merchant.service.IMerchantService;
import org.dromara.game.merchant.service.IPlayerService;
import org.springframework.stereotype.Service;

/**
 * 玩家远程服务实现
 *
 * @author MLChong
 */
@RequiredArgsConstructor
@Service
@DubboService
public class RemotePlayerServiceImpl implements RemotePlayerService {

    private final IMerchantService merchantService;
    private final IPlayerService playerService;

    @Override
    public RemotePlayerVo getOrCreate(Long merchantId, String externalPlayerId) {
        MerchantVo merchant = merchantService.queryById(merchantId);
        if (merchant == null || !"0".equals(merchant.getStatus())) {
            throw new ServiceException("商户不存在或已停用");
        }
        MerchantPlayer player = playerService.getOrCreate(merchantId, externalPlayerId);
        RemotePlayerVo vo = new RemotePlayerVo();
        vo.setPlayerId(player.getPlayerId());
        vo.setMerchantId(player.getMerchantId());
        vo.setExternalPlayerId(player.getExternalPlayerId());
        vo.setStatus(player.getStatus());
        return vo;
    }
}
