package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.PongMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// PING을 받으면 보낸 사람에게만 clientTime을 그대로 담은 PONG을 보내는지 검증한다
class PingWsHandlerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final RoomBroadcaster broadcaster = mock(RoomBroadcaster.class);
    private final PingWsHandler handler = new PingWsHandler(broadcaster, objectMapper);

    private final WsMessageContext ctx = new WsMessageContext(null, "ABC123", "p1", null);

    @Test
    void PING_타입을_담당한다() {
        assertEquals(MessageType.PING, handler.type());
    }

    @Test
    void 보낸_사람에게만_clientTime을_그대로_담아_PONG을_보낸다() {
        long before = System.currentTimeMillis();

        handler.handle(ctx, objectMapper.readTree("{\"clientTime\":100}"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).sendTo(eq("ABC123"), eq("p1"), eq(MessageType.PONG), captor.capture());
        PongMessage pong = (PongMessage) captor.getValue();
        assertEquals(100, pong.clientTime());
        assertTrue(pong.serverTime() >= before);
    }

    @Test
    void 방_전체에는_보내지_않는다() {
        handler.handle(ctx, objectMapper.readTree("{\"clientTime\":100}"));

        verify(broadcaster, never()).broadcast(any(), any(), any());
    }
}