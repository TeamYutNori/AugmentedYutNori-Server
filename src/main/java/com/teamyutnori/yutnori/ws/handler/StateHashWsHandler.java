package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.common.ForbiddenException;
import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.game.exception.GameErrorCode;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.service.StateDesyncEvent;
import com.teamyutnori.yutnori.game.service.StateHashVerifier;
import com.teamyutnori.yutnori.game.service.StateHashVerifier.Result;
import com.teamyutnori.yutnori.game.service.StateHashVerifier.Status;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsRequests.StateHashReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class StateHashWsHandler implements WsMessageHandler {

    private final StateHashVerifier verifier;
    private final GameSessionRepository sessionRepository;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;

    @Override
    public MessageType type() {
        return MessageType.STATE_HASH;
    }

    @Override
    public void handle(WsMessageContext context, JsonNode payload) {
        StateHashReport report = objectMapper.treeToValue(payload, StateHashReport.class);
        if (report.seq() <= 0) {
            throw new InvalidRequestException("INVALID_SEQ", "seq는 1 이상이어야 합니다.");
        }

        int playerCount = playerCountOf(context.roomCode(), context.playerId());
        Result result = verifier.submit(context.roomCode(), report.seq(), context.playerId(),
                report.stateHash(), playerCount);

        if (result.status() == Status.DESYNC) {
            log.warn("상태 불일치 room={} seq={} hashes={}", result.roomCode(), result.seq(), result.hashes());
            publisher.publishEvent(new StateDesyncEvent(result.roomCode(), result.seq(), result.hashes()));
        }
    }

    private int playerCountOf(String roomCode, String playerId){
        GameSession session = sessionRepository.find(roomCode).orElseThrow(() -> new NotFoundException(GameErrorCode.GAME_NOT_FOUND, "진행 중인 게임이 없습니다: " + roomCode));
        synchronized (session){
            if(session.teamOf(playerId).isEmpty())
                throw new ForbiddenException(GameErrorCode.PLAYER_NOT_IN_GAME, "게임에 참가하지 않은 플레이어입니다.");
            return session.getPlayerTeams().size();
        }
    }
}
