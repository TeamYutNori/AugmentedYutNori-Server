package com.teamyutnori.yutnori.common;

public class ConflictException extends BusinessException {
    public ConflictException(String code, String message) {
        super(code, message);
    }
}
