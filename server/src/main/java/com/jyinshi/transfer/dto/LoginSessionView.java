package com.jyinshi.transfer.dto;

import com.jyinshi.transfer.entity.TransferLoginSession;
import lombok.Data;

/** 前端轮询加号会话状态（不含凭据）。 */
@Data
public class LoginSessionView {
    private String sessionId;
    private String workerId;
    private String panType;
    private String mode;
    private String status;
    private String accountName;
    private String message;

    public static LoginSessionView of(TransferLoginSession s) {
        LoginSessionView v = new LoginSessionView();
        v.sessionId = s.getSessionId();
        v.workerId = s.getWorkerId();
        v.panType = s.getPanType();
        v.mode = s.getMode();
        v.status = s.getStatus();
        v.accountName = s.getAccountName();
        v.message = s.getMessage();
        return v;
    }
}
