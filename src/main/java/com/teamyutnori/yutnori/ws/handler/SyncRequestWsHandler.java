package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.game.exception.GameErrorCode;
import com.teamyutnori.yutnori.reconnect.ReconnectService;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class SyncRequestWsHandler implements WsMessageHandler{

    private final ReconnectService reconnectService;

    @Override
    public MessageType type() {
        return MessageType.SYNC_REQUEST;
    }

    @Override
    public void handle(WsMessageContext context, JsonNode payload) {
        if (!reconnectService.sendSnapshot(context.roomCode(), context.playerId())) {
            throw new NotFoundException(GameErrorCode.GAME_NOT_FOUND, "진행 중인 게임이 없습니다.");
        }
    }
}
