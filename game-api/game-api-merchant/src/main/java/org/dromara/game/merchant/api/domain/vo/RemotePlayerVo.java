package org.dromara.game.merchant.api.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 玩家远程视图对象
 *
 * @author MLChong
 */
@Data
public class RemotePlayerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 玩家ID */
    private Long playerId;

    /** 所属商户ID */
    private Long merchantId;

    /** 商户侧玩家标识 */
    private String externalPlayerId;

    /** 状态（0正常 1停用） */
    private String status;
}
