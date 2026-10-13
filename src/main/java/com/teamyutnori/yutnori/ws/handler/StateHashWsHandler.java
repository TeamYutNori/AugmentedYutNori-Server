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
import com.teamyutnori.yutnori.ws.RoomSessionRegistry;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsRequests.StateHashReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

// STATE_HASH: MOVE_APPLIED 적용 후 각 클라가 보낸 상태 해시를 모아 비교한다
// 기다리는 인원 = 게임에서 나가지 않았고 지금 WebSocket이 연결된 플레이어
// 끊긴 사람은 해시를 못 보내므로 세지 않는다 (자동 진행 중에도 비교가 끝나도록)
@Slf4j
@Component
@RequiredArgsConstructor
public class StateHashWsHandler implements WsMessageHandler {

    private final StateHashVerifier verifier;
    private final GameSessionRepository sessionRepository;
    private final RoomSessionRegistry registry;
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

        int expected = connectedPlayerCount(context.roomCode(), context.playerId());
        Result result = verifier.submit(context.roomCode(), report.seq(), context.playerId(),
                report.stateHash(), expected);

        if (result.status() == Status.DESYNC) {
            log.warn("상태 불일치 room={} seq={} hashes={}", result.roomCode(), result.seq(), result.hashes());
            publisher.publishEvent(new StateDesyncEvent(result.roomCode(), result.seq(), result.hashes()));
        }
    }

    // 해시를 보낼 수 있는 인원: 게임 참가자 중 나가지 않았고 지금 연결된 사람
    // 게임이 없거나, 보낸 사람이 참가자가 아니거나, 이미 나간 사람이면 거부
    private int connectedPlayerCount(String roomCode, String playerId) {
        GameSession session = sessionRepository.find(roomCode)
                .orElseThrow(() -> new NotFoundException(GameErrorCode.GAME_NOT_FOUND, "진행 중인 게임이 없습니다: " + roomCode));

        synchronized (session) {
            Integer senderTeam = session.getPlayerTeams().get(playerId);
            if (senderTeam == null || session.isRemoved(senderTeam)) {
                throw new ForbiddenException(GameErrorCode.PLAYER_NOT_IN_GAME, "게임에 참가 중인 플레이어가 아닙니다.");
            }

            int count = 0;
            for (Map.Entry<String, Integer> entry : session.getPlayerTeams().entrySet()) {
                boolean removed = session.isRemoved(entry.getValue());
                boolean connected = registry.find(roomCode, entry.getKey()).isPresent();
                if (!removed && connected) count++;
            }
            return Math.max(count, 1);   // 보낸 사람은 연결돼 있으므로 최소 1
        }
    }
}