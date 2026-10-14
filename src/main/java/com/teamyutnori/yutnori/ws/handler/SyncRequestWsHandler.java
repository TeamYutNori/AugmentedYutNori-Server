package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.ForbiddenException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.game.exception.GameErrorCode;
import com.teamyutnori.yutnori.reconnect.ReconnectErrorCode;
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
        switch (reconnectService.sendSnapshot(context.roomCode(), context.playerId())) {
            case SENT -> { }
            case GAME_NOT_FOUND -> throw new NotFoundException(GameErrorCode.GAME_NOT_FOUND,
                    "진행 중인 게임이 없습니다.");
            case NOT_IN_GAME -> throw new ForbiddenException(GameErrorCode.PLAYER_NOT_IN_GAME,
                    "이 게임의 참가자가 아닙니다.");
            case REMOVED -> throw new ForbiddenException(ReconnectErrorCode.REMOVED_FROM_GAME,
                    "게임에서 이미 제외되어 복귀할 수 없습니다.");
            case GAME_ENDED -> throw new ConflictException(ReconnectErrorCode.GAME_ALREADY_ENDED,
                    "이미 끝난 게임입니다.");
        }
    }
}
