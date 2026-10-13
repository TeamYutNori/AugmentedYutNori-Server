package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.game.augment.effect.ContrarianAugment;
import com.teamyutnori.yutnori.game.augment.effect.SprinterAugment;
import com.teamyutnori.yutnori.game.board.BoardState;
import com.teamyutnori.yutnori.game.board.TestBoards;
import com.teamyutnori.yutnori.game.service.AutoMovePolicy.AutoMove;
import com.teamyutnori.yutnori.game.yut.YutResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// 기본 판: 0 출발 → 1 ... 19 → 20(참먹이) → 0 골인, 5·10번 모서리 지름길
// 팀 0: 말 0, 1 / 팀 1: 말 100, 101
class AutoMovePolicyTest {

    private final AutoMovePolicy policy = new AutoMovePolicy();
    private BoardState board;

    @BeforeEach
    void setUp() {
        board = new BoardState(TestBoards.defaultBoard(), List.of(0, 1, 100, 101));
    }

    private void move(int pieceId, int moveCount, int destination) {
        assertTrue(board.tryMove(pieceId, moveCount, destination).isPresent(),
                "준비 이동 실패: " + pieceId + ", " + moveCount + " → " + destination);
    }

    private AutoMove choose(YutResult... stored) {
        return policy.choose(board, 0, List.of(stored)).orElseThrow();
    }

    // 고른 이동은 서버 판 규칙(tryMove)을 그대로 통과해야 한다
    private void assertApplicable(AutoMove m) {
        assertTrue(board.tryMove(m.pieceId(), m.moveCount(), m.destinationNodeId()).isPresent(), "적용 불가: " + m);
    }

    @Test
    @DisplayName("대기 말뿐이면 번호가 작은 말을 낸다")
    void waitingPiece() {
        AutoMove m = choose(YutResult.Gae);
        assertEquals(0, m.pieceId());
        assertEquals(2, m.destinationNodeId());
        assertApplicable(m);
    }

    @Test
    @DisplayName("가장 앞선 말을 움직인다")
    void mostAdvanced() {
        move(0, 4, 4); move(0, 5, 9);   // 말 0: 9번
        move(1, 2, 2);                   // 말 1: 2번
        AutoMove m = choose(YutResult.Do);
        assertEquals(0, m.pieceId());
        assertApplicable(m);
    }

    @Test
    @DisplayName("잡을 수 있으면 앞선 말보다 잡기가 먼저")
    void captureFirst() {
        move(0, 4, 4); move(0, 5, 9);   // 말 0: 9번
        move(1, 1, 1);                   // 말 1: 1번
        move(100, 3, 3);                 // 상대: 3번
        AutoMove m = choose(YutResult.Gae);
        assertEquals(1, m.pieceId());
        assertEquals(3, m.destinationNodeId());
        assertEquals(1, m.captures());
        assertApplicable(m);
    }

    @Test
    @DisplayName("골인할 수 있으면 잡기보다 골인이 먼저")
    void finishFirst() {
        move(0, 4, 4); move(0, 5, 9); move(0, 5, 14); move(0, 5, 19);   // 말 0: 19번
        move(1, 1, 1);                                                  // 말 1: 1번
        move(100, 3, 3);                                                // 상대: 3번
        AutoMove m = choose(YutResult.Gae);
        assertEquals(0, m.pieceId());
        assertTrue(m.finishes());
        assertApplicable(m);
    }

    @Test
    @DisplayName("빽도는 다른 수가 없을 때만")
    void backDoLast() {
        move(0, 2, 2);
        AutoMove forward = choose(YutResult.BackDo, YutResult.Do);
        assertFalse(forward.backward());
        assertEquals(1, forward.moveCount());

        AutoMove back = choose(YutResult.BackDo);
        assertTrue(back.backward());
        assertEquals(1, back.destinationNodeId());
        assertApplicable(back);
    }

    @Test
    @DisplayName("청개구리: 빽도로 앞으로 가는 길도 후보가 되고, 앞으로 간다")
    void contrarian() {
        board.addAugment(new ContrarianAugment(0));
        move(0, 3, 3);
        AutoMove m = choose(YutResult.BackDo);
        assertEquals(-1, m.moveCount());
        assertEquals(4, m.destinationNodeId());
        assertFalse(m.backward());
        assertApplicable(m);
    }

    @Test
    @DisplayName("칸 수 보정 증강도 반영 (스프린터: 새 말을 올릴 때 +1 → 도로 2번 칸)")
    void sprinter() {
        board.addAugment(new SprinterAugment(0));
        AutoMove m = choose(YutResult.Do);
        assertEquals(1, m.moveCount());          // 보낼 때는 굴린 칸 수 그대로
        assertEquals(2, m.destinationNodeId());  // 도착은 보정된 칸
        assertApplicable(m);
    }

    @Test
    @DisplayName("둘 수 있는 이동이 없으면 empty (대기 말만 있는데 빽도)")
    void noMove() {
        assertTrue(policy.choose(board, 0, List.of(YutResult.BackDo)).isEmpty());
    }
}
