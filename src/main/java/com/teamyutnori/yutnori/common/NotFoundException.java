package com.teamyutnori.yutnori.common;

public class NotFoundException extends BusinessException {
    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
