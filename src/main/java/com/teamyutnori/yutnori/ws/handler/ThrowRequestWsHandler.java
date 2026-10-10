package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.game.service.GameService;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// THROW_REQUEST: 윷 던지기 요청 (payload 없음) → GameService.throwYut
@Component
@RequiredArgsConstructor
public class ThrowRequestWsHandler implements WsMessageHandler {

    private final GameService gameService;

    @Override
    public MessageType type() {
        return MessageType.THROW_REQUEST;
    }

    @Override
    public void handle(WsMessageContext context, JsonNode payload) {
        gameService.throwYut(context.roomCode(), context.playerId());
    }
}
