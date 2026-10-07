package com.teamyutnori.yutnori.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final RoomSessionRegistry registry;
    private final MessageRouter router;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WsMessageContext context = WsMessageContext.from(session);
        registry.register(context.roomCode(), context.playerId(), session);
        log.info("WS 연결 room={} player={}", context.roomCode(), context.playerId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        router.route(WsMessageContext.from(session), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        WsMessageContext context = WsMessageContext.from(session);
        registry.unregister(context.roomCode(), context.playerId(), session);
        log.info("WS 종료 room={} player={} status={}",
                context.roomCode(), context.playerId(), status);
        // TODO: 7번 ConnectionCleanup - 방에 연결 끊김 알림, 재접속 대기 처리
    }
}
