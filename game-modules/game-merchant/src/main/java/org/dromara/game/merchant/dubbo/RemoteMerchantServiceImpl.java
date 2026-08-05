package org.dromara.game.merchant.dubbo;

import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.game.merchant.api.RemoteMerchantService;
import org.dromara.game.merchant.api.domain.vo.RemoteMerchantVo;
import org.dromara.game.merchant.domain.Merchant;
import org.dromara.game.merchant.service.IMerchantService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 商户远程服务实现
 *
 * @author MLChong
 */
@RequiredArgsConstructor
@Service
@DubboService
public class RemoteMerchantServiceImpl implements RemoteMerchantService {

    private final IMerchantService merchantService;

    @Override
    public RemoteMerchantVo getByCode(String merchantCode) {
        Merchant merchant = merchantService.getByCode(merchantCode);
        if (merchant == null) {
            return null;
        }
        RemoteMerchantVo vo = new RemoteMerchantVo();
        vo.setMerchantId(merchant.getMerchantId());
        vo.setMerchantCode(merchant.getMerchantCode());
        vo.setMerchantName(merchant.getMerchantName());
        vo.setStatus(merchant.getStatus());
        vo.setSecret(merchant.getSecret());
        vo.setWalletMode(merchant.getWalletMode());
        vo.setCallbackUrl(merchant.getCallbackUrl());
        vo.setFeeRate(merchant.getFeeRate());
        vo.setPrepaidBalance(merchant.getPrepaidBalance());
        List<String> currencies = StringUtils.isBlank(merchant.getCurrencies())
            ? List.of()
            : Arrays.asList(merchant.getCurrencies().split(","));
        vo.setCurrencies(currencies);
        return vo;
    }
}
