package com.teamyutnori.yutnori.ws;

import com.teamyutnori.yutnori.common.ForbiddenException;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.ErrorMessage;
import com.teamyutnori.yutnori.ws.handler.PingWsHandler;
import com.teamyutnori.yutnori.ws.handler.WsMessageHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// 받은 JSON을 type에 맞는 핸들러로 넘기는지, 잘못된 메시지·핸들러 예외를 ERROR로 바꿔 보내는지 검증한다
class MessageRouterTest {

    private static final String ROOM = "ABC123";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final RoomBroadcaster broadcaster = mock(RoomBroadcaster.class);
    private final WsMessageContext ctx = new WsMessageContext(null, ROOM, "p1", null);

    private WsMessageHandler pingHandler;
    private MessageRouter router;

    @BeforeEach
    void setUp() {
        pingHandler = mock(WsMessageHandler.class);
        when(pingHandler.type()).thenReturn(MessageType.PING);
        router = new MessageRouter(List.of(pingHandler), broadcaster, objectMapper);
    }

    // 보낸 사람에게 ERROR가 갔는지 확인하고 그 code를 돌려준다
    private String sentErrorCode() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).sendTo(eq(ROOM), eq("p1"), eq(MessageType.ERROR), captor.capture());
        return ((ErrorMessage) captor.getValue()).code();
    }

    // 핸들러가 받은 payload를 꺼낸다
    private JsonNode handledPayload() {
        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(pingHandler).handle(eq(ctx), captor.capture());
        return captor.getValue();
    }

    // ── 정상 ──

    @Test
    void type에_맞는_핸들러로_payload를_넘긴다() {
        router.route(ctx, "{\"type\":\"PING\",\"seq\":0,\"payload\":{\"clientTime\":100}}");

        assertEquals(100, handledPayload().get("clientTime").asLong());
        verify(broadcaster, never()).sendTo(any(), any(), eq(MessageType.ERROR), any());
    }

    @Test
    void payload가_없으면_빈_객체로_넘긴다() {
        router.route(ctx, "{\"type\":\"PING\",\"seq\":0}");

        JsonNode payload = handledPayload();
        assertTrue(payload.isObject());
        assertEquals(0, payload.size());
    }

    @Test
    void payload가_null이면_빈_객체로_넘긴다() {
        router.route(ctx, "{\"type\":\"PING\",\"seq\":0,\"payload\":null}");

        JsonNode payload = handledPayload();
        assertTrue(payload.isObject());
        assertEquals(0, payload.size());
    }

    // ── INVALID_JSON ──

    @Test
    void 깨진_JSON이면_INVALID_JSON() {
        router.route(ctx, "{\"type\":");

        assertEquals("INVALID_JSON", sentErrorCode());
        verify(pingHandler, never()).handle(any(), any());
    }

    @Test
    void 객체가_아닌_JSON이면_INVALID_JSON() {
        router.route(ctx, "123");

        assertEquals("INVALID_JSON", sentErrorCode());
    }

    // ── UNKNOWN_TYPE ──

    @Test
    void 없는_type이면_UNKNOWN_TYPE() {
        router.route(ctx, "{\"type\":\"FOO\",\"seq\":0,\"payload\":{}}");

        assertEquals("UNKNOWN_TYPE", sentErrorCode());
        verify(pingHandler, never()).handle(any(), any());
    }

    @Test
    void 서버만_보내는_type이면_UNKNOWN_TYPE() {
        router.route(ctx, "{\"type\":\"PONG\",\"seq\":0,\"payload\":{}}");

        assertEquals("UNKNOWN_TYPE", sentErrorCode());
    }

    @Test
    void type이_없으면_UNKNOWN_TYPE() {
        router.route(ctx, "{\"seq\":0,\"payload\":{}}");

        assertEquals("UNKNOWN_TYPE", sentErrorCode());
    }

    // ── 핸들러 예외 ──

    @Test
    void 핸들러가_BusinessException을_던지면_그_code로_ERROR() {
        doThrow(new ForbiddenException("NOT_YOUR_TURN", "내 차례가 아닙니다."))
                .when(pingHandler).handle(any(), any());

        router.route(ctx, "{\"type\":\"PING\",\"seq\":0,\"payload\":{}}");

        assertEquals("NOT_YOUR_TURN", sentErrorCode());
    }

    @Test
    void payload_필드_타입이_틀리면_INVALID_REQUEST() {
        // 실제 PingWsHandler로 treeToValue 실패(JacksonException)를 일으킨다
        MessageRouter realRouter = new MessageRouter(
                List.of(new PingWsHandler(broadcaster, objectMapper)), broadcaster, objectMapper);

        realRouter.route(ctx, "{\"type\":\"PING\",\"seq\":0,\"payload\":{\"clientTime\":\"abc\"}}");

        assertEquals("INVALID_REQUEST", sentErrorCode());
    }

    @Test
    void 예상치_못한_예외는_INTERNAL_ERROR() {
        doThrow(new RuntimeException("버그")).when(pingHandler).handle(any(), any());

        router.route(ctx, "{\"type\":\"PING\",\"seq\":0,\"payload\":{}}");

        assertEquals("INTERNAL_ERROR", sentErrorCode());
    }

    // ── 등록 ──

    @Test
    void 같은_type_핸들러가_둘이면_생성에_실패한다() {
        WsMessageHandler another = mock(WsMessageHandler.class);
        when(another.type()).thenReturn(MessageType.PING);

        assertThrows(IllegalStateException.class,
                () -> new MessageRouter(List.of(pingHandler, another), broadcaster, objectMapper));
    }
}