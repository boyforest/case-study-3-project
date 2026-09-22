package com.greenhill.coop.common;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException badRequest(String message) {
        return new BizException(BizCode.PARAM_ERROR.getCode(), message);
    }

    public static BizException notFound(String message) {
        return new BizException(BizCode.NOT_FOUND.getCode(), message);
    }

    public static BizException conflict(String message) {
        return new BizException(BizCode.CONFLICT.getCode(), message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(BizCode.UNAUTHORIZED.getCode(), message);
    }

    public static BizException forbidden(String message) {
        return new BizException(BizCode.FORBIDDEN.getCode(), message);
    }
}
