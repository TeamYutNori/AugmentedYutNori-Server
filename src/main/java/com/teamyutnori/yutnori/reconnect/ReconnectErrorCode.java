package com.teamyutnori.yutnori.reconnect;

// 재접속·복귀 요청 거절 코드 (SYNC_REQUEST 응답 ERROR.code)
public final class ReconnectErrorCode {
    private ReconnectErrorCode() {}

    public static final String REMOVED_FROM_GAME = "REMOVED_FROM_GAME";   // Forbidden: 나가기·재접속 실패로 이미 빠진 플레이어
    public static final String GAME_ALREADY_ENDED = "GAME_ALREADY_ENDED"; // Conflict: 이미 끝난 경기
}