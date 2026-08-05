package org.dromara.game.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.game.merchant.domain.MerchantPlayer;
import org.dromara.game.merchant.domain.vo.PlayerVo;
import org.dromara.game.merchant.mapper.MerchantPlayerMapper;
import org.dromara.game.merchant.service.IPlayerService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 玩家档案服务层实现
 *
 * @author MLChong
 */
@RequiredArgsConstructor
@Service
public class PlayerServiceImpl implements IPlayerService {

    private final MerchantPlayerMapper baseMapper;

    @Override
    public MerchantPlayer getOrCreate(Long merchantId, String externalPlayerId) {
        MerchantPlayer player = selectByUk(merchantId, externalPlayerId);
        if (player == null) {
            MerchantPlayer insert = new MerchantPlayer();
            insert.setMerchantId(merchantId);
            insert.setExternalPlayerId(externalPlayerId);
            insert.setStatus("0");
            try {
                baseMapper.insert(insert);
                player = insert;
            } catch (DuplicateKeyException e) {
                // 并发建档撞唯一键，重查即可
                player = selectByUk(merchantId, externalPlayerId);
            }
        }
        if (player == null) {
            throw new ServiceException("玩家建档失败");
        }
        if (!"0".equals(player.getStatus())) {
            throw new ServiceException("玩家已停用");
        }
        return player;
    }

    private MerchantPlayer selectByUk(Long merchantId, String externalPlayerId) {
        return baseMapper.selectOne(Wrappers.<MerchantPlayer>lambdaQuery()
            .eq(MerchantPlayer::getMerchantId, merchantId)
            .eq(MerchantPlayer::getExternalPlayerId, externalPlayerId));
    }

    @Override
    public TableDataInfo<PlayerVo> queryPageList(Long merchantId, String externalPlayerId, PageQuery pageQuery) {
        LambdaQueryWrapper<MerchantPlayer> lqw = Wrappers.lambdaQuery();
        lqw.eq(merchantId != null, MerchantPlayer::getMerchantId, merchantId);
        lqw.like(StringUtils.isNotBlank(externalPlayerId), MerchantPlayer::getExternalPlayerId, externalPlayerId);
        lqw.orderByDesc(MerchantPlayer::getPlayerId);
        Page<PlayerVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public int changeStatus(Long playerId, String status) {
        MerchantPlayer update = new MerchantPlayer();
        update.setPlayerId(playerId);
        update.setStatus(status);
        return baseMapper.updateById(update);
    }
}
