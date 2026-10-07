package com.teamyutnori.yutnori.ws.dto;

// 서버 → 클라이언트 메시지 payload 모음
public final class WsMessages {
    private WsMessages() {}

    public record PongMessage(long clientTime, long serverTime) {}
    public record ErrorMessage(String code, String message) {}
    public record PlayerDisconnectedMessage(String playerId, int graceSec) {}
    public record PlayerReconnectedMessage(String playerId) {}
}
