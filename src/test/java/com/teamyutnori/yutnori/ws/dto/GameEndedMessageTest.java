package com.teamyutnori.yutnori.ws.dto;

import com.teamyutnori.yutnori.game.model.GameEndReason;
import com.teamyutnori.yutnori.ws.dto.WsMessages.GameEndedMessage;
import com.teamyutnori.yutnori.ws.dto.WsMessages.RankingEntry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// GAME_ENDED payload가 클라 GameMessages.cs와 같은 필드 이름으로 나가는지 검증한다
class GameEndedMessageTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void 순위_목록이_클라와_같은_필드_이름으로_나간다() {
        GameEndedMessage message = new GameEndedMessage(0, GameEndReason.FINISHED, List.of(
                new RankingEntry("p1", 0, 1, 500),
                new RankingEntry("p2", 1, 2, 400)));

        JsonNode expected = objectMapper.readTree("""
                {"winnerTeam":0,"reason":"FINISHED","rankings":[
                  {"playerId":"p1","team":0,"rank":1,"rewardGold":500},
                  {"playerId":"p2","team":1,"rank":2,"rewardGold":400}]}""");
        assertEquals(expected, objectMapper.valueToTree(message));
    }

    @Test
    void 인자_2개로_만들면_rankings가_빈_배열로_나간다() {
        GameEndedMessage message = new GameEndedMessage(1, GameEndReason.DISCONNECTED);

        JsonNode expected = objectMapper.readTree(
                "{\"winnerTeam\":1,\"reason\":\"DISCONNECTED\",\"rankings\":[]}");
        assertEquals(expected, objectMapper.valueToTree(message));
    }

    @Test
    void rankings에_null을_넣으면_빈_목록이_된다() {
        GameEndedMessage message = new GameEndedMessage(0, GameEndReason.FINISHED, null);

        assertEquals(List.of(), message.rankings());
    }
}