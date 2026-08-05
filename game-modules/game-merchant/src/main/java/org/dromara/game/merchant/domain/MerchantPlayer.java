package org.dromara.game.merchant.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 玩家档案表 merchant_player
 *
 * @author MLChong
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("merchant_player")
public class MerchantPlayer extends BaseEntity {

    /**
     * 玩家ID
     */
    @TableId(value = "player_id")
    private Long playerId;

    /**
     * 所属商户ID
     */
    private Long merchantId;

    /**
     * 商户侧玩家标识
     */
    private String externalPlayerId;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;
}
