package org.dromara.game.merchant.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.game.merchant.domain.Merchant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 商户视图对象 merchant
 *
 * @author MLChong
 */
@Data
@AutoMapper(target = Merchant.class)
public class MerchantVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 商户ID
     */
    private Long merchantId;

    /**
     * 商户编码
     */
    private String merchantCode;

    /**
     * 商户名称
     */
    private String merchantName;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 钱包模式（TRANSFER/SEAMLESS）
     */
    private String walletMode;

    /**
     * seamless回调地址
     */
    private String callbackUrl;

    /**
     * 支持币种（逗号分隔大写ISO码）
     */
    private String currencies;

    /**
     * 费率（0~1）
     */
    private BigDecimal feeRate;

    /**
     * 预存额度余额
     */
    private BigDecimal prepaidBalance;

    /**
     * IP白名单（逗号分隔）
     */
    private String ipWhitelist;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private Date createTime;
}
