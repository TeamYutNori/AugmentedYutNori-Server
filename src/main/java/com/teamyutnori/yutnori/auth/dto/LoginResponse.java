package com.teamyutnori.yutnori.auth.dto;

public record LoginResponse(
        String playerId,
        String nickname,
        String token
) {
}