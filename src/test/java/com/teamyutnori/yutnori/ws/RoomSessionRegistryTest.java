package com.teamyutnori.yutnori.ws;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// RoomSessionRegistry의 등록·해제·조회와 재접속 시 세션 교체를 검증한다
class RoomSessionRegistryTest {

    private static final String ROOM = "ABC123";

    private final RoomSessionRegistry registry = new RoomSessionRegistry();

    // 테스트용 가짜 세션. getId()만 원하는 값을 돌려준다
    private WebSocketSession session(String id) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        return s;
    }

    @Test
    void 등록하면_find로_찾을_수_있다() {
        registry.register(ROOM, "p1", session("s1"));

        // 저장된 건 Decorator로 감싼 세션이라 객체가 아니라 id로 비교한다
        assertEquals("s1", registry.find(ROOM, "p1").orElseThrow().getId());
    }

    @Test
    void 같은_방에_두_명_등록하면_세션이_두_개다() {
        registry.register(ROOM, "p1", session("s1"));
        registry.register(ROOM, "p2", session("s2"));

        assertEquals(2, registry.getSessions(ROOM).size());
    }

    @Test
    void 해제하면_find가_비어있다() {
        WebSocketSession s1 = session("s1");
        registry.register(ROOM, "p1", s1);
        registry.register(ROOM, "p2", session("s2"));

        registry.unregister(ROOM, "p1", s1);

        assertTrue(registry.find(ROOM, "p1").isEmpty());
        assertEquals(1, registry.getSessions(ROOM).size());
    }

    @Test
    void 마지막_사람이_나가면_방이_비워진다() {
        WebSocketSession s1 = session("s1");
        registry.register(ROOM, "p1", s1);

        registry.unregister(ROOM, "p1", s1);

        assertTrue(registry.getSessions(ROOM).isEmpty());
    }

    @Test
    void 같은_플레이어가_재등록하면_새_세션으로_교체된다() {
        registry.register(ROOM, "p1", session("old"));
        registry.register(ROOM, "p1", session("new"));

        assertEquals("new", registry.find(ROOM, "p1").orElseThrow().getId());
        assertEquals(1, registry.getSessions(ROOM).size());
    }

    @Test
    void 재접속_후_옛_세션이_해제돼도_새_세션은_남는다() {
        WebSocketSession oldSession = session("old");
        registry.register(ROOM, "p1", oldSession);
        registry.register(ROOM, "p1", session("new"));

        // 옛 연결의 종료 이벤트가 늦게 도착한 상황
        registry.unregister(ROOM, "p1", oldSession);

        assertEquals("new", registry.find(ROOM, "p1").orElseThrow().getId());
    }

    @Test
    void 없는_방을_조회하면_빈_결과다() {
        assertTrue(registry.getSessions("NOPE").isEmpty());
        assertTrue(registry.find("NOPE", "p1").isEmpty());
    }
}