package org.dromara.game.merchant.api;

import org.dromara.game.merchant.api.domain.vo.RemoteMerchantVo;

/**
 * 商户远程服务
 *
 * @author MLChong
 */
public interface RemoteMerchantService {

    /**
     * 按商户编码查询商户（含 secret，供 openapi 验签）
     *
     * @param merchantCode 商户编码
     * @return 商户信息，不存在返回 null
     */
    RemoteMerchantVo getByCode(String merchantCode);
}
