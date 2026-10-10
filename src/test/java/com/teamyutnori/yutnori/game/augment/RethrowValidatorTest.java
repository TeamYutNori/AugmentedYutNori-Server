package com.teamyutnori.yutnori.game.augment;

import com.teamyutnori.yutnori.game.board.TestBoards;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.service.TurnManager;
import com.teamyutnori.yutnori.game.yut.YutResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Unity RethrowAugment.CanUse + GameRules.TryRethrow 조건과 같은지 확인
class RethrowValidatorTest {

    private final RethrowValidator validator =
            new RethrowValidator(new AugmentCatalog(JsonMapper.builder().build()));
    private final TurnManager turnManager = new TurnManager();
    private GameSession session;

    @BeforeEach
    void setUp() {
        session = new GameSession("ROOM", new GameSetup(2, 2, "default", false), Map.of("p0", 0, "p1", 1), TestBoards.defaultBoard());
        turnManager.startFirstTurn(session);
        session.addAugment(0, RethrowValidator.RETHROW_AUGMENT_ID);
    }

    @Test
    @DisplayName("증강 보유 + 개 + 이동 전 → 가능")
    void allowed() {
        turnManager.applyThrow(session, YutResult.Gae);
        assertTrue(validator.canRethrow(session, 0));
    }

    @Test
    @DisplayName("증강이 없으면 불가")
    void noAugment() {
        turnManager.applyThrow(session, YutResult.Gae);
        session.removeAugment(0, RethrowValidator.RETHROW_AUGMENT_ID);
        assertFalse(validator.canRethrow(session, 0));
    }

    @Test
    @DisplayName("아직 안 던졌으면 불가")
    void noResultYet() {
        assertFalse(validator.canRethrow(session, 0));
    }

    @Test
    @DisplayName("윷·모는 다시 던질 수 없다")
    void notOnYutOrMo() {
        turnManager.applyThrow(session, YutResult.Mo);
        assertFalse(validator.canRethrow(session, 0));
    }

    @Test
    @DisplayName("이번 턴에 말을 움직였으면 불가")
    void notAfterMove() {
        turnManager.applyThrow(session, YutResult.Gae);
        session.setMovedThisTurn(true);
        assertFalse(validator.canRethrow(session, 0));
    }
}
