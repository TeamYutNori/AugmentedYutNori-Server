package com.teamyutnori.yutnori.common;

import lombok.Getter;

// 클라이언트에 code와 함께 알려야 하는 예외의 공통 부모. REST는 GlobalExceptionHandler, WS는 MessageRouter가 처리
@Getter
public abstract class BusinessException extends RuntimeException {
    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
