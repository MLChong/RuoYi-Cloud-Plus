package org.dromara.game.merchant.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.game.merchant.domain.MerchantPlayer;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 玩家档案视图对象 merchant_player
 *
 * @author MLChong
 */
@Data
@AutoMapper(target = MerchantPlayer.class)
public class PlayerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 玩家ID
     */
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
     * 创建时间
     */
    private Date createTime;
}
