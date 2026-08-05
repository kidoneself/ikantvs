package com.jyinshi.common.exception;

import com.jyinshi.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常。Service 层校验/规则不通过时抛出，由 {@link GlobalExceptionHandler} 统一转 Result。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = ResultCode.BIZ_ERROR.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(ResultCode rc) {
        super(rc.getMessage());
        this.code = rc.getCode();
    }
}
