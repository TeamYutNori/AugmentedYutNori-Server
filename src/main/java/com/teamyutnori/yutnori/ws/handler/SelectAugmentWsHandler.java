package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.game.dto.GameRequests.SelectAugmentRequest;
import com.teamyutnori.yutnori.game.service.GameService;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// SELECT_AUGMENT: 증강 선택 → GameService.selectAugment
@Component
@RequiredArgsConstructor
public class SelectAugmentWsHandler implements WsMessageHandler {

    private final GameService gameService;
    private final ObjectMapper objectMapper;

    @Override
    public MessageType type() {
        return MessageType.SELECT_AUGMENT;
    }

    @Override
    public void handle(WsMessageContext context, JsonNode payload) {
        SelectAugmentRequest request = objectMapper.treeToValue(payload, SelectAugmentRequest.class);
        gameService.selectAugment(context.roomCode(), context.playerId(), request.augmentId());
    }
}
