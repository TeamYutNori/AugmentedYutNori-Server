package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.common.InvalidRequestException;
import com.teamyutnori.yutnori.config.GameProperties;
import com.teamyutnori.yutnori.game.service.StateDesyncEvent;
import com.teamyutnori.yutnori.game.service.StateHashVerifier;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateHashWsHandlerTest {

    private static final String ROOM = "ABC123";
    private static final String HASH_A = "0123456789abcdef";
    private static final String HASH_B = "fedcba9876543210";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final List<Object> events = new ArrayList<>();
    private final GameProperties properties = new GameProperties(
            2, 2, Duration.ofSeconds(30), 3, Duration.ofSeconds(20), Duration.ofSeconds(30));
    private final StateHashWsHandler handler = new StateHashWsHandler(
            new StateHashVerifier(), properties, events::add, objectMapper);

    private final WsMessageContext p1 = new WsMessageContext(null, ROOM, "p1", null);
    private final WsMessageContext p2 = new WsMessageContext(null, ROOM, "p2", null);

    // STATE_HASH 타입을 담당한다
    @Test
    void type() {
        assertThat(handler.type()).isEqualTo(MessageType.STATE_HASH);
    }

    // 한 명만 보내면 아직 비교하지 않는다 (이벤트 없음)
    @Test
    void pending() {
        handler.handle(p1, report(1, HASH_A));

        assertThat(events).isEmpty();
    }

    // 둘 다 같은 해시면 이벤트 없음
    @Test
    void match() {
        handler.handle(p1, report(1, HASH_A));
        handler.handle(p2, report(1, HASH_A));

        assertThat(events).isEmpty();
    }

    // 해시가 다르면 StateDesyncEvent가 한 번 발행되고, 양쪽 해시가 담긴다
    @Test
    void desync() {
        handler.handle(p1, report(1, HASH_A));
        handler.handle(p2, report(1, HASH_B));

        assertThat(events).hasSize(1);
        StateDesyncEvent event = (StateDesyncEvent) events.get(0);
        assertThat(event.roomCode()).isEqualTo(ROOM);
        assertThat(event.seq()).isEqualTo(1);
        assertThat(event.hashes()).containsEntry("p1", HASH_A).containsEntry("p2", HASH_B);
    }

    // seq가 0 이하면 INVALID_SEQ
    @Test
    void invalidSeq() {
        assertThatThrownBy(() -> handler.handle(p1, report(0, HASH_A)))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_SEQ");
    }

    // 해시 형식이 잘못되면 Verifier의 INVALID_STATE_HASH가 그대로 전달된다
    @Test
    void invalidHash() {
        assertThatThrownBy(() -> handler.handle(p1, report(1, "1234")))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_STATE_HASH");
    }

    private tools.jackson.databind.JsonNode report(long seq, String hash) {
        return objectMapper.readTree("{\"seq\":" + seq + ",\"stateHash\":\"" + hash + "\"}");
    }
}