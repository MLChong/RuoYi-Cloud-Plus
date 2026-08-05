package org.dromara.game.merchant.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.game.merchant.domain.Merchant;
import org.dromara.game.merchant.domain.bo.MerchantBo;
import org.dromara.game.merchant.domain.vo.MerchantVo;

import java.math.BigDecimal;

/**
 * 商户服务层
 *
 * @author MLChong
 */
public interface IMerchantService {

    /**
     * 分页查询商户列表
     */
    TableDataInfo<MerchantVo> queryPageList(MerchantBo bo, PageQuery pageQuery);

    /**
     * 按ID查询商户
     */
    MerchantVo queryById(Long merchantId);

    /**
     * 按编码查询商户（含secret，服务内部使用）
     */
    Merchant getByCode(String merchantCode);

    /**
     * 新增商户（自动生成密钥）
     */
    int insertMerchant(MerchantBo bo);

    /**
     * 修改商户（编码与密钥不可修改）
     */
    int updateMerchant(MerchantBo bo);

    /**
     * 重置密钥并返回新密钥（仅此一次透出）
     */
    String resetSecret(Long merchantId);

    /**
     * 原子调整预存额度（delta 可正可负，结果不允许为负）
     */
    void adjustPrepaid(Long merchantId, BigDecimal delta);

    /**
     * 批量逻辑删除
     */
    int deleteByIds(Long[] merchantIds);
}
