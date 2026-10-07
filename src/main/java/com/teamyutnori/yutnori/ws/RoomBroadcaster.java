package com.teamyutnori.yutnori.ws;

import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Optional;

// 서버 → 클라 메시지 전송 담당. 봉투에 담아 JSON으로 바꿔 Registry의 안전한 세션으로 보낸다
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomBroadcaster {

    private final RoomSessionRegistry registry;
    private final SequenceTracker sequenceTracker;
    private final ObjectMapper objectMapper;

    // 방 전체에 보낸다. 방 seq를 1 증가시켜 붙인 seq를 반환한다
    public long broadcast(String roomCode, MessageType type, Object payload) {
        long seq = sequenceTracker.next(roomCode);
        TextMessage textMessage = toMessage(type, seq, payload);
        for (WebSocketSession session : registry.getSessions(roomCode)) {
            send(session, textMessage);
        }
        return seq;
    }

    // 한 명에게만 보낸다 (PONG, ERROR 등). seq는 0 = 순서 검사 대상 아님
    public void sendTo(String roomCode, String playerId, MessageType type, Object payload) {
        TextMessage textMessage = toMessage(type, 0, payload);
        Optional<WebSocketSession> session = registry.find(roomCode, playerId);
        session.ifPresent(foundSession -> send(foundSession, textMessage));
    }

    // 재접속한 한 명에게 이미 보냈던 메시지를 원래 seq로 다시 보낸다. 방 seq는 증가시키지 않는다
    public void resendTo(String roomCode, String playerId, MessageType type, long seq, Object payload) {
        TextMessage textMessage = toMessage(type, seq, payload);
        Optional<WebSocketSession> session = registry.find(roomCode, playerId);
        session.ifPresent(foundSession -> send(foundSession, textMessage));
    }

    // 봉투에 담아 JSON 텍스트로 만든다. payload가 없으면 {}
    private TextMessage toMessage(MessageType type, long seq, Object payload) {
        JsonNode body = payload == null ? objectMapper.createObjectNode() :
                objectMapper.valueToTree(payload);
        return new TextMessage(objectMapper.writeValueAsString(new WsEnvelope(type, seq, body)));
    }

    // 한 세션에 보낸다. 닫혔거나 실패해도 예외를 던지지 않아 다른 세션 전송은 계속된다
    private void send(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(message);
        } catch (IOException e) {
            log.warn("메시지 전송 실패 session={}", session.getId(), e);
        }
    }
}
