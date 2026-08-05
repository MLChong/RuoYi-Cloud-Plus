package org.dromara.game.merchant.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.game.merchant.domain.bo.MerchantBo;
import org.dromara.game.merchant.domain.vo.MerchantVo;
import org.dromara.game.merchant.service.IMerchantService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 商户管理
 *
 * @author MLChong
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/merchant")
public class MerchantController extends BaseController {

    private final IMerchantService merchantService;

    /**
     * 查询商户列表
     */
    @SaCheckPermission("game:merchant:list")
    @GetMapping("/list")
    public TableDataInfo<MerchantVo> list(MerchantBo bo, PageQuery pageQuery) {
        return merchantService.queryPageList(bo, pageQuery);
    }

    /**
     * 获取商户详细信息
     */
    @SaCheckPermission("game:merchant:query")
    @GetMapping("/{merchantId}")
    public R<MerchantVo> getInfo(@PathVariable Long merchantId) {
        return R.ok(merchantService.queryById(merchantId));
    }

    /**
     * 新增商户
     */
    @SaCheckPermission("game:merchant:add")
    @Log(title = "商户管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody MerchantBo bo) {
        return toAjax(merchantService.insertMerchant(bo));
    }

    /**
     * 修改商户
     */
    @SaCheckPermission("game:merchant:edit")
    @Log(title = "商户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody MerchantBo bo) {
        return toAjax(merchantService.updateMerchant(bo));
    }

    /**
     * 重置商户密钥（新密钥仅在本响应中透出一次）
     */
    @SaCheckPermission("game:merchant:resetSecret")
    @Log(title = "商户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/resetSecret/{merchantId}")
    public R<String> resetSecret(@PathVariable Long merchantId) {
        return R.ok("重置成功", merchantService.resetSecret(merchantId));
    }

    /**
     * 调整商户预存额度（正数增加 负数扣减）
     */
    @SaCheckPermission("game:merchant:prepaid")
    @Log(title = "商户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/prepaid/{merchantId}")
    public R<Void> adjustPrepaid(@PathVariable Long merchantId,
                                 @NotNull(message = "调整金额不能为空") @RequestParam BigDecimal delta) {
        merchantService.adjustPrepaid(merchantId, delta);
        return R.ok();
    }

    /**
     * 删除商户
     */
    @SaCheckPermission("game:merchant:remove")
    @Log(title = "商户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{merchantIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] merchantIds) {
        return toAjax(merchantService.deleteByIds(merchantIds));
    }
}
