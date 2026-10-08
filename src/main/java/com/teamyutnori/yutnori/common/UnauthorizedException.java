package com.teamyutnori.yutnori.common;

public class UnauthorizedException extends BusinessException {
    public UnauthorizedException(String code, String message) {
        super(code, message);
    }
}
