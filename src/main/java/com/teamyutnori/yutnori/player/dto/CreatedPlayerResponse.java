package com.teamyutnori.yutnori.player.dto;

// 플레이어 생성 응답 DTO
public record CreatedPlayerResponse(

        // 외부에서 사용하는 플레이어 식별자
        String playerId,

        // 플레이어 닉네임
        String nickname,

        // WebSocket 연결 시 사용하는 게스트 토큰
        String guestToken
) {
}