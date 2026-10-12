package com.teamyutnori.yutnori.game.dto;

import com.teamyutnori.yutnori.game.board.MoveResult;
import com.teamyutnori.yutnori.game.board.TestBoards;
import com.teamyutnori.yutnori.game.dto.GameMessages.MoveAppliedMessage;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

// MOVE_APPLIED payload에 auto가 클라와 같은 이름으로 나가는지 검증한다
class MoveAppliedMessageTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void 자동_진행_이동이면_auto가_true로_나간다() {
        MoveAppliedMessage message = new MoveAppliedMessage(
                0, 1, 2, 3, List.of(1), List.of(), false, "hash", true);

        JsonNode json = objectMapper.valueToTree(message);

        assertTrue(json.get("auto").asBoolean());
    }

    @Test
    void auto를_추가해도_기존_필드_이름은_그대로다() {
        MoveAppliedMessage message = new MoveAppliedMessage(
                0, 1, 2, 3, List.of(1), List.of(100), true, "hash", false);

        JsonNode expected = objectMapper.readTree("""
                {"team":0,"pieceId":1,"moveCount":2,"destinationNodeId":3,
                 "movedPieceIds":[1],"capturedPieceIds":[100],
                 "isFinished":true,"stateHash":"hash","auto":false}""");
        assertEquals(expected, objectMapper.valueToTree(message));
    }

    @Test
    void of에_auto를_넘기면_그대로_담긴다() {
        GameSession session = new GameSession("ROOM", new GameSetup(2, 2, "default", false),
                Map.of("p0", 0, "p1", 1), TestBoards.defaultBoard());
        MoveResult result = session.getBoardState().tryMove(0, 2, 2).orElseThrow();   // 팀 0의 0번 말, 개(2칸) → 2번 칸

        assertTrue(MoveAppliedMessage.of(0, result, "hash", true).auto());
        assertFalse(MoveAppliedMessage.of(0, result, "hash").auto());
    }
}