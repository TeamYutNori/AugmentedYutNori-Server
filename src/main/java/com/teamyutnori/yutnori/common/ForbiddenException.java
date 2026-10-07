package com.teamyutnori.yutnori.common;

public class ForbiddenException extends BusinessException {
    public ForbiddenException(String code, String message) {
        super(code, message);
    }
}
