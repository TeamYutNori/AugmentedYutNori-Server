package com.teamyutnori.yutnori.common;

public class InvalidRequestException extends BusinessException {
    public InvalidRequestException(String code, String message) {
        super(code, message);
    }
}
