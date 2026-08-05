package org.dromara.game.merchant.api.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商户远程视图对象（含 secret，仅限服务间调用，严禁透出到任何 HTTP 响应）
 *
 * @author MLChong
 */
@Data
public class RemoteMerchantVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商户ID */
    private Long merchantId;

    /** 商户编码 */
    private String merchantCode;

    /** 商户名称 */
    private String merchantName;

    /** 状态（0正常 1停用） */
    private String status;

    /** API 签名密钥 */
    private String secret;

    /** 钱包模式（TRANSFER/SEAMLESS） */
    private String walletMode;

    /** seamless 回调地址 */
    private String callbackUrl;

    /** 支持币种列表（大写 ISO 码，如 CNY/USDT） */
    private List<String> currencies;

    /** 费率（0~1 小数） */
    private BigDecimal feeRate;

    /** 预存额度余额 */
    private BigDecimal prepaidBalance;
}
