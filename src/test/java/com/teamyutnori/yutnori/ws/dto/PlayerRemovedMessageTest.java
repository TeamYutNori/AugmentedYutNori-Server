package com.teamyutnori.yutnori.ws.dto;

import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.ws.dto.WsMessages.PlayerRemovedMessage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

// PLAYER_REMOVED payload가 정한 필드 이름(playerId, team, reason)으로 나가는지 검증한다
class PlayerRemovedMessageTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void 나가기로_제거되면_FORFEIT으로_나간다() {
        PlayerRemovedMessage message = new PlayerRemovedMessage("p1", 2, GameEndReason.FORFEIT);

        JsonNode expected = objectMapper.readTree(
                "{\"playerId\":\"p1\",\"team\":2,\"reason\":\"FORFEIT\"}");
        assertEquals(expected, objectMapper.valueToTree(message));
    }

    @Test
    void 재접속_실패로_제거되면_DISCONNECTED로_나간다() {
        PlayerRemovedMessage message = new PlayerRemovedMessage("p2", 0, GameEndReason.DISCONNECTED);

        JsonNode expected = objectMapper.readTree(
                "{\"playerId\":\"p2\",\"team\":0,\"reason\":\"DISCONNECTED\"}");
        assertEquals(expected, objectMapper.valueToTree(message));
    }
}
