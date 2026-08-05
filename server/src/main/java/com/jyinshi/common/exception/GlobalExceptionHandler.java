package com.jyinshi.common.exception;

import com.jyinshi.common.api.Result;
import com.jyinshi.common.api.ResultCode;
import com.jyinshi.common.security.ip.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 全局异常处理：把各类异常统一转成 {@link Result}，避免异常细节泄露给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidation(BindException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getDefaultMessage() : ResultCode.BAD_REQUEST.getMessage();
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /** 缺参 / 类型不对 / 缺 multipart：业务 400，不打 ERROR 堆栈。 */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class
    })
    public Result<Void> handleBadRequest(Exception e, HttpServletRequest request) {
        String detail;
        if (e instanceof MissingServletRequestParameterException m) {
            detail = "缺少参数: " + m.getParameterName();
        } else if (e instanceof MissingServletRequestPartException m) {
            detail = "缺少文件: " + m.getRequestPartName();
        } else if (e instanceof MethodArgumentTypeMismatchException m) {
            detail = "参数类型错误: " + m.getName();
        } else {
            detail = ResultCode.BAD_REQUEST.getMessage();
        }
        log.warn("请求参数错误 {} {} ip={} ua={} — {}",
                request.getMethod(), request.getRequestURI(),
                ClientIpResolver.resolve(request),
                ua(request), detail);
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), detail);
    }

    /** 客户端中途断开（流式搜索关掉页等），不是服务端故障。 */
    @ExceptionHandler(ClientAbortException.class)
    public Result<Void> handleClientAbort(ClientAbortException e, HttpServletRequest request) {
        log.debug("客户端断开 {} {} ip={}",
                request.getMethod(), request.getRequestURI(), ClientIpResolver.resolve(request));
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "客户端已断开");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e, HttpServletRequest request) {
        // 嵌套的 Broken pipe 偶发不走 ClientAbortException（被包装进别的异常）
        if (isClientAbort(e)) {
            return handleClientAbort(new ClientAbortException(e.getMessage()), request);
        }
        log.error("未捕获异常 {} {} ip={}",
                request.getMethod(), request.getRequestURI(),
                ClientIpResolver.resolve(request), e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }

    private static String ua(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) {
            return "-";
        }
        return ua.length() > 160 ? ua.substring(0, 160) : ua;
    }

    private static boolean isClientAbort(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ClientAbortException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("Broken pipe") || msg.contains("Connection reset"))) {
                return true;
            }
        }
        return false;
    }
}
