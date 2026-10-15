package com.teamyutnori.yutnori.game.augment.effect;

import com.teamyutnori.yutnori.game.augment.AugmentCatalog;
import com.teamyutnori.yutnori.game.augment.AugmentEffects;
import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.BoardState;
import com.teamyutnori.yutnori.game.board.MoveResult;
import com.teamyutnori.yutnori.game.board.TestBoards;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// 이동 증강 8종이 Unity Core/Augment/Augments와 같은 결과를 내는지 확인
// 기본 판: 0 출발 → 1~4 → 5(모서리, 지름길 21) → 6~9 → 10(모서리) ... 20 참먹이 / 21, 22 → 23(방)
// 팀 0 말 = 0, 1 / 팀 1 말 = 100, 101
class BoardAugmentEffectTest {

    private BoardState state;

    @BeforeEach
    void setUp() {
        state = new BoardState(TestBoards.defaultBoard(), List.of(0, 1, 100, 101));
    }

    private MoveResult move(int pieceId, int moveCount, int destination) {
        Optional<MoveResult> result = state.tryMove(pieceId, moveCount, destination);
        assertTrue(result.isPresent(), "이동 실패: piece " + pieceId + ", " + moveCount + "칸 → " + destination);
        return result.get();
    }

    private int nodeOf(int pieceId) { return state.getPiece(pieceId).getNode().getId(); }

    @Test
    @DisplayName("청개구리: 빽도로 뒤(2) 또는 앞(4) 둘 다 고를 수 있다")
    void contrarian() {
        state.addAugment(new ContrarianAugment(0));
        move(0, 3, 3);
        assertEquals(2, state.getMovePaths(state.getPiece(0), -1).size());
        move(0, -1, 4);
    }

    @Test
    @DisplayName("청개구리: 대기 말도 빽도로 1칸 출발할 수 있다, 상대 말은 영향 없음")
    void contrarianFromWaiting() {
        state.addAugment(new ContrarianAugment(0));
        move(0, -1, 1);
        assertTrue(state.getMovePaths(state.getPiece(100), -1).isEmpty());
    }

    @Test
    @DisplayName("스프린터: 새 말 출발 때만 +1칸")
    void sprinter() {
        state.addAugment(new SprinterAugment(0));
        move(0, 1, 2);   // 도 + 1
        move(0, 1, 3);   // 판 위 말은 그대로
        move(100, 1, 1); // 상대는 영향 없음
    }

    @Test
    @DisplayName("수행자: 도·개만 +1칸")
    void monk() {
        state.addAugment(new MonkAugment(0));
        move(0, 2, 3);   // 개 + 1
        move(0, 3, 6);   // 걸은 그대로 (3 → 6)
    }

    @Test
    @DisplayName("마라토너: +1칸, 모서리에 멈춰도 지름길 대신 원래 길로만")
    void marathoner() {
        state.addAugment(new MarathonerAugment(0));
        move(0, 2, 3);   // 개 + 1
        move(0, 1, 5);   // 도 + 1 → 모서리 5에 멈춤
        var paths = state.getMovePaths(state.getPiece(0), 2);
        assertEquals(1, paths.size());
        assertEquals(8, paths.get(0).destination().getId());   // 5 → 6, 7, 8 (지름길 21 아님)
        move(0, 2, 8);
    }

    @Test
    @DisplayName("외톨이: 판에 내 말이 있으면 새 말은 출발 못 함, 대신 안 잡힘")
    void loner() {
        state.addAugment(new LonerAugment(0));
        move(0, 2, 2);
        assertTrue(state.getMovePaths(state.getPiece(1), 2).isEmpty());

        MoveResult result = move(100, 2, 2);   // 상대가 같은 칸에 와도
        assertEquals(0, result.capturedCount());
        assertTrue(state.getPiece(0).isOnBoard());
    }

    @Test
    @DisplayName("평화주의자: 잡지도 못하고 잡히지도 않는다")
    void pacifist() {
        state.addAugment(new PacifistAugment(0));
        move(100, 3, 3);
        assertEquals(0, move(0, 3, 3).capturedCount());   // 못 잡음
        assertEquals(0, move(101, 3, 3).capturedCount()); // 안 잡힘
    }

    @Test
    @DisplayName("암살자: 도착 칸이 아니라 한 칸 앞의 상대 말을 잡는다")
    void assassin() {
        state.addAugment(new AssassinAugment(0));
        move(100, 3, 3);
        move(101, 4, 4);
        MoveResult result = move(0, 3, 3);
        assertEquals(List.of(state.getPiece(101)), result.capturedPieces());
        assertTrue(state.getPiece(100).isOnBoard(), "도착 칸의 말은 안 잡힘");
        assertTrue(state.getPiece(101).isWaiting());
    }

    @Test
    @DisplayName("경호원: 업힌 말이 잡혀도 한 마리만 대기석으로")
    void friendShield() {
        state.addAugment(new FriendShieldAugment(1));
        move(100, 2, 2);
        move(101, 2, 2);
        assertEquals(1, move(0, 2, 2).capturedCount());
        long left = state.getPiecesOf(1).stream().filter(p -> p.isOnBoard()).count();
        assertEquals(1, left);
    }

    @Test
    @DisplayName("암살자(10) → 경호원(20) 순서: 업힌 말은 한 마리만 잡힌다")
    void assassinThenFriendShield() {
        state.addAugment(new FriendShieldAugment(1));   // 등록 순서와 상관없이 우선순위대로
        state.addAugment(new AssassinAugment(0));
        move(100, 4, 4);
        move(101, 4, 4);
        assertEquals(1, move(0, 3, 3).capturedCount());
    }

    @Test
    @DisplayName("AugmentEffects: 이동 증강은 객체 생성, 야바위꾼·사냥꾼·도박사는 판 효과 없음")
    void effectsFactory() {
        AugmentEffects effects = new AugmentEffects(new AugmentCatalog(JsonMapper.builder().build()));
        for (String id : List.of(AugmentIds.CONTRARIAN, AugmentIds.SPRINTER, AugmentIds.MONK, AugmentIds.FRIEND_SHIELD,
                AugmentIds.PACIFIST, AugmentIds.MARATHONER, AugmentIds.ASSASSIN, AugmentIds.LONER)) {
            var created = effects.create(id, 1);
            assertTrue(created.isPresent(), id);
            assertEquals(id, created.get().id());
            assertEquals(1, created.get().ownerTeam());
        }
        for (String id : List.of(AugmentIds.RETHROW, AugmentIds.HUNTER, AugmentIds.GAMBLER)) {
            assertTrue(effects.create(id, 0).isEmpty(), id);
        }
    }
}
