package com.teamyutnori.yutnori.common;

import lombok.Getter;

@Getter
public class ForbiddenException extends RuntimeException {

    private final String code;

    public ForbiddenException(String code, String message) {
        super(message);
        this.code = code;
    }
}
