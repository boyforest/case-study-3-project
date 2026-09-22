package com.greenhill.coop.common;

import lombok.Getter;

@Getter
public enum BizCode {
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "Invalid request"),
    UNAUTHORIZED(401, "Not logged in"),
    FORBIDDEN(403, "No permission"),
    NOT_FOUND(404, "Not found"),
    CONFLICT(409, "Conflict"),
    SYSTEM_ERROR(500, "System error");

    private final int code;
    private final String message;

    BizCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
