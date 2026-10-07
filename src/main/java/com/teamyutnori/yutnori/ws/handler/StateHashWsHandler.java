package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.config.GameProperties;
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
    private final GameProperties gameProperties;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;

    @Override
    public MessageType type() {
        return MessageType.STATE_HASH;
    }

    @Override
    public void handle(WsMessageContext context, JsonNode payload) {
        StateHashReport report = objectMapper.treeToValue(payload, StateHashReport.class);
        if(report.seq() <= 0) throw new InvalidRequestException("INVALID_SEQ", "seq는 1 이상이어야 합니다.");

        Result result = verifier.submit(context.roomCode(), report.seq(), context.playerId(), report.stateHash(), gameProperties.maxPlayers());

        if(result.status() == Status.DESYNC){
            log.warn("상태 불일치 room={} seq={} hashes={}", result.roomCode(), result.seq(), result.hashes());
            publisher.publishEvent(new StateDesyncEvent(result.roomCode(), result.seq(), result.hashes()));
        }
    }
}
