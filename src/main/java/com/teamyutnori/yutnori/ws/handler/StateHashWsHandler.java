package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.game.model.GamePhase;
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
import java.util.Optional;

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
        if (report.seq() <= 0) return;   // 잘못된 seq는 비교하지 않고 무시 (확인용 메시지라 에러를 보내지 않음)

        int expected = connectedPlayerCount(context.roomCode(), context.playerId());
        if (expected < 2) return;   // 게임 없음·참가자 아님·혼자 연결됨 → 비교할 상대가 없음

        Result result = verifier.submit(context.roomCode(), report.seq(), context.playerId(),
                report.stateHash(), expected);

        if (result.status() == Status.DESYNC) {
            log.warn("상태 불일치 room={} seq={} hashes={}", result.roomCode(), result.seq(), result.hashes());
            publisher.publishEvent(new StateDesyncEvent(result.roomCode(), result.seq(), result.hashes()));
        }
    }

    // 해시를 보낼 수 있는 인원: 게임 참가자 중 나가지 않았고 지금 연결된 사람
    // 비교할 수 없는 경우는 0을 돌려준다 (예외 대신):
    //  - 게임 세션 없음 (게임 종료 직후 세션이 정리된 뒤 늦게 온 해시 등)
    //  - 보낸 사람이 참가자가 아니거나 이미 게임에서 빠짐
    private int connectedPlayerCount(String roomCode, String playerId) {
        Optional<GameSession> found = sessionRepository.find(roomCode);
        if (found.isEmpty()) return 0;
        GameSession session = found.get();

        synchronized (session) {
            if (session.getPhase() == GamePhase.ENDED) return 0;

            Integer senderTeam = session.getPlayerTeams().get(playerId);
            if (senderTeam == null || session.isRemoved(senderTeam)) return 0;

            int count = 0;
            for (Map.Entry<String, Integer> entry : session.getPlayerTeams().entrySet()) {
                boolean removed = session.isRemoved(entry.getValue());
                boolean connected = registry.find(roomCode, entry.getKey()).isPresent();
                if (!removed && connected) count++;
            }
            return count;
        }
    }
}