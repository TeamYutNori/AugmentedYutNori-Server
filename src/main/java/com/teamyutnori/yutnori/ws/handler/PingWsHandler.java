package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.PongMessage;
import com.teamyutnori.yutnori.ws.dto.WsRequests.PingMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PingWsHandler implements WsMessageHandler {

    private final RoomBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    @Override
    public MessageType type() {
        return MessageType.PING;
    }

    @Override
    public void handle(WsMessageContext context, JsonNode payload) {
        PingMessage ping = objectMapper.treeToValue(payload, PingMessage.class);
        PongMessage pong = new PongMessage(ping.clientTime(), System.currentTimeMillis());
        broadcaster.sendTo(context.roomCode(), context.playerId(), MessageType.PONG, pong);
    }
}
