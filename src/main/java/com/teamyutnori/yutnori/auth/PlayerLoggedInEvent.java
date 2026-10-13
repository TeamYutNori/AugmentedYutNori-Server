package com.teamyutnori.yutnori.auth;

// 로그인 성공 시 AuthController가 발행 (업적: 접속 횟수)
public record PlayerLoggedInEvent(String playerId) {}
