package org.dromara.game.merchant.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.game.merchant.domain.Merchant;

import java.math.BigDecimal;

/**
 * 商户业务对象 merchant
 *
 * @author MLChong
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = Merchant.class, reverseConvertGenerate = false)
public class MerchantBo extends BaseEntity {

    /**
     * 商户ID
     */
    private Long merchantId;

    /**
     * 商户编码（4-32位字母数字下划线）
     */
    @NotBlank(message = "商户编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "商户编码须为4-32位字母数字下划线")
    private String merchantCode;

    /**
     * 商户名称
     */
    @NotBlank(message = "商户名称不能为空")
    @Size(max = 50, message = "商户名称不能超过{max}个字符")
    private String merchantName;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 钱包模式（TRANSFER/SEAMLESS）
     */
    @NotBlank(message = "钱包模式不能为空")
    @Pattern(regexp = "^(TRANSFER|SEAMLESS)$", message = "钱包模式仅支持TRANSFER/SEAMLESS")
    private String walletMode;

    /**
     * seamless回调地址
     */
    @Size(max = 500, message = "回调地址不能超过{max}个字符")
    private String callbackUrl;

    /**
     * 支持币种（逗号分隔大写ISO码）
     */
    @NotBlank(message = "支持币种不能为空")
    @Pattern(regexp = "^[A-Z]{3,10}(,[A-Z]{3,10})*$", message = "币种须为逗号分隔的大写代码")
    private String currencies;

    /**
     * 费率（0~1）
     */
    @NotNull(message = "费率不能为空")
    @DecimalMin(value = "0", message = "费率不能小于0")
    @DecimalMax(value = "1", message = "费率不能大于1")
    private BigDecimal feeRate;

    /**
     * IP白名单（逗号分隔）
     */
    @Size(max = 500, message = "IP白名单不能超过{max}个字符")
    private String ipWhitelist;

    /**
     * 备注
     */
    private String remark;
}
