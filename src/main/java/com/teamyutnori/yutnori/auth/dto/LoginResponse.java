package com.teamyutnori.yutnori.auth.dto;

public record LoginResponse(
        String playerId,
        String nickname,
        String token,
        int gold,
        boolean guest   // 게스트(익명 로그인) 계정이면 true. 클라가 "기록이 이 기기에만 남아요" 안내에 쓴다
) {
}