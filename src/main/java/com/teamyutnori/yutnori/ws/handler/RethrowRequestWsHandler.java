package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.game.dto.GameRequests.RethrowRequest;
import com.teamyutnori.yutnori.game.service.GameService;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// RETHROW_REQUEST: 다시 던지기 증강 사용 → GameService.rethrow
@Component
@RequiredArgsConstructor
public class RethrowRequestWsHandler implements WsMessageHandler {

    private final GameService gameService;
    private final ObjectMapper objectMapper;

    @Override
    public MessageType type() {
        return MessageType.RETHROW_REQUEST;
    }

    @Override
    public void handle(WsMessageContext context, JsonNode payload) {
        RethrowRequest request = objectMapper.treeToValue(payload, RethrowRequest.class);
        gameService.rethrow(context.roomCode(), context.playerId(), request.augmentId());
    }
}
