package com.teamyutnori.yutnori.ws;

import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.PongMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 서버가 보내는 JSON이 클라 봉투 모양과 같은지, seq 부여 규칙, 전송 실패 시 동작을 검증한다
class RoomBroadcasterTest {

    private static final String ROOM = "ABC123";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final RoomSessionRegistry registry = new RoomSessionRegistry();
    private final RoomBroadcaster broadcaster =
            new RoomBroadcaster(registry, new SequenceTracker(), objectMapper);

    // 열려 있는 가짜 세션
    private WebSocketSession openSession(String id) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    // 세션으로 마지막에 전송된 메시지를 JSON으로 꺼낸다
    private JsonNode lastSent(WebSocketSession session) throws IOException {
        ArgumentCaptor<WebSocketMessage<?>> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        TextMessage message = (TextMessage) captor.getValue();
        return objectMapper.readTree(message.getPayload());
    }

    @Test
    void sendTo는_클라_봉투_모양으로_seq_0을_붙여_보낸다() throws IOException {
        WebSocketSession s1 = openSession("s1");
        registry.register(ROOM, "p1", s1);

        broadcaster.sendTo(ROOM, "p1", MessageType.PONG, new PongMessage(100, 200));

        JsonNode expected = objectMapper.readTree(
                "{\"type\":\"PONG\",\"seq\":0,\"payload\":{\"clientTime\":100,\"serverTime\":200}}");
        assertEquals(expected, lastSent(s1));
    }

    @Test
    void broadcast는_방_전원에게_보내고_seq가_1씩_증가한다() throws IOException {
        WebSocketSession s1 = openSession("s1");
        WebSocketSession s2 = openSession("s2");
        registry.register(ROOM, "p1", s1);
        registry.register(ROOM, "p2", s2);

        broadcaster.broadcast(ROOM, MessageType.TURN_CHANGED, null);
        assertEquals(1, lastSent(s1).get("seq").asLong());
        assertEquals(1, lastSent(s2).get("seq").asLong());

        broadcaster.broadcast(ROOM, MessageType.TURN_CHANGED, null);
        assertEquals(2, lastSent(s1).get("seq").asLong());
        assertEquals(2, lastSent(s2).get("seq").asLong());
    }

    @Test
    void payload가_null이면_빈_객체로_보낸다() throws IOException {
        WebSocketSession s1 = openSession("s1");
        registry.register(ROOM, "p1", s1);

        broadcaster.broadcast(ROOM, MessageType.GAME_ENDED, null);

        assertEquals(objectMapper.createObjectNode(), lastSent(s1).get("payload"));
    }

    @Test
    void 닫힌_세션은_건너뛰고_나머지에게는_보낸다() throws IOException {
        WebSocketSession closed = mock(WebSocketSession.class);
        when(closed.getId()).thenReturn("closed");
        when(closed.isOpen()).thenReturn(false);
        WebSocketSession open = openSession("open");
        registry.register(ROOM, "p1", closed);
        registry.register(ROOM, "p2", open);

        broadcaster.broadcast(ROOM, MessageType.TURN_CHANGED, null);

        verify(closed, never()).sendMessage(any());
        verify(open).sendMessage(any());
    }

    @Test
    void 한_세션_전송이_실패해도_나머지에게는_보낸다() throws IOException {
        WebSocketSession broken = openSession("broken");
        doThrow(new IOException("끊김")).when(broken).sendMessage(any());
        WebSocketSession ok = openSession("ok");
        registry.register(ROOM, "p1", broken);
        registry.register(ROOM, "p2", ok);

        broadcaster.broadcast(ROOM, MessageType.TURN_CHANGED, null);

        verify(ok).sendMessage(any());
    }

    @Test
    void 없는_플레이어에게_sendTo하면_아무것도_하지_않는다() {
        broadcaster.sendTo(ROOM, "nobody", MessageType.PONG, new PongMessage(1, 2));
        // 예외 없이 끝나면 통과
    }

    @Test
    void broadcast는_붙인_seq를_반환한다() throws IOException {
        WebSocketSession s1 = openSession("s1");
        registry.register(ROOM, "p1", s1);

        long first = broadcaster.broadcast(ROOM, MessageType.TURN_CHANGED, null);
        assertEquals(1, first);
        assertEquals(first, lastSent(s1).get("seq").asLong());

        long second = broadcaster.broadcast(ROOM, MessageType.TURN_CHANGED, null);
        assertEquals(2, second);
        assertEquals(second, lastSent(s1).get("seq").asLong());
    }

    @Test
    void resendTo는_넘긴_seq를_그대로_붙여_한_명에게만_보낸다() throws IOException {
        WebSocketSession s1 = openSession("s1");
        WebSocketSession s2 = openSession("s2");
        registry.register(ROOM, "p1", s1);
        registry.register(ROOM, "p2", s2);

        broadcaster.resendTo(ROOM, "p1", MessageType.TURN_CHANGED, 5, null);

        JsonNode expected = objectMapper.readTree(
                "{\"type\":\"TURN_CHANGED\",\"seq\":5,\"payload\":{}}");
        assertEquals(expected, lastSent(s1));
        verify(s2, never()).sendMessage(any());
    }

    @Test
    void resendTo는_방_seq를_증가시키지_않는다() throws IOException {
        WebSocketSession s1 = openSession("s1");
        registry.register(ROOM, "p1", s1);

        broadcaster.broadcast(ROOM, MessageType.TURN_CHANGED, null);
        broadcaster.resendTo(ROOM, "p1", MessageType.TURN_CHANGED, 1, null);
        long next = broadcaster.broadcast(ROOM, MessageType.TURN_CHANGED, null);

        assertEquals(2, next);
        assertEquals(2, lastSent(s1).get("seq").asLong());
    }

    @Test
    void 없는_플레이어에게_resendTo하면_아무것도_하지_않는다() {
        broadcaster.resendTo(ROOM, "nobody", MessageType.TURN_CHANGED, 1, null);
        // 예외 없이 끝나면 통과
    }
}