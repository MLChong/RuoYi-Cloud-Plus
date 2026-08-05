package org.dromara.game.merchant.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;

/**
 * 商户表 merchant
 *
 * @author MLChong
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("merchant")
public class Merchant extends BaseEntity {

    /**
     * 商户ID
     */
    @TableId(value = "merchant_id")
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
     * API签名密钥
     */
    private String secret;

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
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}
