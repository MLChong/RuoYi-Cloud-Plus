package org.dromara.game.merchant.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.game.merchant.domain.vo.PlayerVo;
import org.dromara.game.merchant.service.IPlayerService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 玩家档案管理
 *
 * @author MLChong
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/player")
public class PlayerController extends BaseController {

    private final IPlayerService playerService;

    /**
     * 查询玩家列表
     */
    @SaCheckPermission("game:player:list")
    @GetMapping("/list")
    public TableDataInfo<PlayerVo> list(@RequestParam(required = false) Long merchantId,
                                        @RequestParam(required = false) String externalPlayerId,
                                        PageQuery pageQuery) {
        return playerService.queryPageList(merchantId, externalPlayerId, pageQuery);
    }

    /**
     * 启用/停用玩家
     */
    @SaCheckPermission("game:player:edit")
    @Log(title = "玩家档案", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus/{playerId}")
    public R<Void> changeStatus(@PathVariable Long playerId,
                                @Pattern(regexp = "^[01]$", message = "状态仅支持0/1") @RequestParam String status) {
        return toAjax(playerService.changeStatus(playerId, status));
    }
}
