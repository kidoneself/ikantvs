package com.jyinshi.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 加号/换号会话（主站与 worker 中转）。
 *
 * <p>放弃扫码后：主站先拿到凭据（cookie 后台粘贴 / 迅雷 refresh_token 授权回调），
 * 存进 {@link #credential}，worker 拉取后落成账号，凭据用完即弃。</p>
 */
@Data
@TableName("transfer_login_session")
public class TransferLoginSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private String workerId;

    private String panType;

    /** 凭据获取方式：cookie（夸克/百度粘贴）/ oauth（迅雷授权）。 */
    private String mode;

    /** pending（待 worker 领）/ pending_auth（迅雷待授权回调）/ claimed / success / failed / expired。 */
    private String status;

    private String accountName;

    /** worker 应落库的凭据：cookie 整段 或 迅雷 refresh_token（worker 领走即用即弃）。 */
    private String credential;

    private String message;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
