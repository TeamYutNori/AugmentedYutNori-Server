package com.teamyutnori.yutnori.ws.dto;

// 클라이언트 → 서버 메시지 payload 모음
public final class WsRequests {
    private WsRequests() {}

    public record PingMessage(long clientTime) {}
    public record StateHashReport(long seq, String stateHash) {}
}
