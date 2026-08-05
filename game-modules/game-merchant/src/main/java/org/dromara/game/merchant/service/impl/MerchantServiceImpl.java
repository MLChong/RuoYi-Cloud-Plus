package org.dromara.game.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.game.merchant.domain.Merchant;
import org.dromara.game.merchant.domain.bo.MerchantBo;
import org.dromara.game.merchant.domain.vo.MerchantVo;
import org.dromara.game.merchant.mapper.MerchantMapper;
import org.dromara.game.merchant.service.IMerchantService;
import org.dromara.game.merchant.util.SecretGenerator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * 商户服务层实现
 *
 * @author MLChong
 */
@RequiredArgsConstructor
@Service
public class MerchantServiceImpl implements IMerchantService {

    private final MerchantMapper baseMapper;

    @Override
    public TableDataInfo<MerchantVo> queryPageList(MerchantBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<Merchant> lqw = buildQueryWrapper(bo);
        Page<MerchantVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    private LambdaQueryWrapper<Merchant> buildQueryWrapper(MerchantBo bo) {
        LambdaQueryWrapper<Merchant> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getMerchantCode()), Merchant::getMerchantCode, bo.getMerchantCode());
        lqw.like(StringUtils.isNotBlank(bo.getMerchantName()), Merchant::getMerchantName, bo.getMerchantName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), Merchant::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getWalletMode()), Merchant::getWalletMode, bo.getWalletMode());
        lqw.orderByDesc(Merchant::getMerchantId);
        return lqw;
    }

    @Override
    public MerchantVo queryById(Long merchantId) {
        return baseMapper.selectVoById(merchantId);
    }

    @Override
    public Merchant getByCode(String merchantCode) {
        return baseMapper.selectOne(Wrappers.<Merchant>lambdaQuery()
            .eq(Merchant::getMerchantCode, merchantCode));
    }

    @Override
    public int insertMerchant(MerchantBo bo) {
        boolean exists = baseMapper.exists(Wrappers.<Merchant>lambdaQuery()
            .eq(Merchant::getMerchantCode, bo.getMerchantCode()));
        if (exists) {
            throw new ServiceException("商户编码已存在: " + bo.getMerchantCode());
        }
        Merchant merchant = toEntity(bo);
        merchant.setSecret(SecretGenerator.generate());
        merchant.setStatus("0");
        merchant.setPrepaidBalance(BigDecimal.ZERO);
        return baseMapper.insert(merchant);
    }

    @Override
    public int updateMerchant(MerchantBo bo) {
        Merchant merchant = toEntity(bo);
        // 编码与密钥不可通过修改接口变更
        merchant.setMerchantCode(null);
        merchant.setSecret(null);
        return baseMapper.updateById(merchant);
    }

    /**
     * Bo -> Entity 字段拷贝
     * <p>
     * 未使用 {@code MapstructUtils.convert}：其底层 {@code SpringUtils.getBean(Converter.class)}
     * 要求已存在存活的 Spring ApplicationContext，而本服务的单元测试采用纯 Mockito
     * （{@code @ExtendWith(MockitoExtension.class)}，不启动 Spring 容器），
     * 调用会抛出 {@code UtilException: No ConfigurableListableBeanFactory or ApplicationContext injected}。
     * 手动拷贝的字段集合与 mapstruct-plus 为 {@code @AutoMapper(target = Merchant.class)} 生成的映射逐字段等价。
     */
    private Merchant toEntity(MerchantBo bo) {
        Merchant merchant = new Merchant();
        merchant.setSearchValue(bo.getSearchValue());
        merchant.setCreateDept(bo.getCreateDept());
        merchant.setCreateBy(bo.getCreateBy());
        merchant.setCreateTime(bo.getCreateTime());
        merchant.setUpdateBy(bo.getUpdateBy());
        merchant.setUpdateTime(bo.getUpdateTime());
        if (bo.getParams() != null) {
            merchant.setParams(new LinkedHashMap<>(bo.getParams()));
        }
        merchant.setMerchantId(bo.getMerchantId());
        merchant.setMerchantCode(bo.getMerchantCode());
        merchant.setMerchantName(bo.getMerchantName());
        merchant.setStatus(bo.getStatus());
        merchant.setWalletMode(bo.getWalletMode());
        merchant.setCallbackUrl(bo.getCallbackUrl());
        merchant.setCurrencies(bo.getCurrencies());
        merchant.setFeeRate(bo.getFeeRate());
        merchant.setIpWhitelist(bo.getIpWhitelist());
        merchant.setRemark(bo.getRemark());
        return merchant;
    }

    @Override
    public String resetSecret(Long merchantId) {
        Merchant db = baseMapper.selectById(merchantId);
        if (db == null) {
            throw new ServiceException("商户不存在: " + merchantId);
        }
        String secret = SecretGenerator.generate();
        Merchant update = new Merchant();
        update.setMerchantId(merchantId);
        update.setSecret(secret);
        baseMapper.updateById(update);
        return secret;
    }

    @Override
    public void adjustPrepaid(Long merchantId, BigDecimal delta) {
        if (delta == null || delta.signum() == 0) {
            throw new ServiceException("调整金额不能为空或0");
        }
        LambdaUpdateWrapper<Merchant> luw = Wrappers.<Merchant>lambdaUpdate()
            .setSql("prepaid_balance = prepaid_balance + {0}", delta)
            .eq(Merchant::getMerchantId, merchantId)
            .apply(true, "prepaid_balance + {0} >= 0", delta);
        int rows = baseMapper.update(null, luw);
        if (rows == 0) {
            throw new ServiceException("额度调整失败：商户不存在或余额不足");
        }
    }

    @Override
    public int deleteByIds(Long[] merchantIds) {
        return baseMapper.deleteByIds(Arrays.asList(merchantIds));
    }
}
