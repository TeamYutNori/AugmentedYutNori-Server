package com.teamyutnori.yutnori.ws.dto;

// 클라이언트 → 서버 메시지 payload 모음
public final class  WsRequests {
    private WsRequests() {}

    public record PingMessage(long clientTime) {}
    public record StateHashReport(long seq, String stateHash) {}
    // 플레이어 준비 상태 변경 요청
    public record ReadyMessage(boolean ready) {}
}
