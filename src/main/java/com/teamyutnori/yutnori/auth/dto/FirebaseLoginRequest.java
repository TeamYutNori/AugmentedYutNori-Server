package com.teamyutnori.yutnori.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record FirebaseLoginRequest(

        // Firebase 로그인 후 클라이언트가 받은 ID Token
        @NotBlank
        String idToken

) {
}