package com.teamyutnori.yutnori.game.board;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// 기본 판(default.json) 기준 이동 규칙이 Unity BoardGraph/BoardState와 같은지 확인
// 칸 번호: 0 출발 / 1~19 바깥길 / 5·10·15 모서리 / 20 참먹이 / 21~29 대각선 (23 = 방)
class BoardStateTest {

    private BoardGraph graph;
    private BoardState state;

    // 팀 0: 말 0, 1 / 팀 1: 말 100, 101
    @BeforeEach
    void setUp() {
        graph = TestBoards.defaultBoard();
        state = new BoardState(graph, List.of(0, 1, 100, 101));
    }

    private MoveResult move(int pieceId, int moveCount, int destination) {
        Optional<MoveResult> result = state.tryMove(pieceId, moveCount, destination);
        assertTrue(result.isPresent(), "이동 실패: piece " + pieceId + ", " + moveCount + "칸 → " + destination);
        return result.get();
    }

    @Test
    @DisplayName("모든 말은 대기석에서 시작")
    void startsWaiting() {
        for (Piece piece : state.getPieces()) assertTrue(piece.isWaiting());
        assertEquals(1, state.getPiece(100).getTeam());
    }

    @Test
    @DisplayName("출발 + 모(5) → 첫 모서리(5번)")
    void moFromStart() {
        move(0, 5, 5);
        assertTrue(state.getPiece(0).isOnBoard());
        assertEquals(5, state.getPiece(0).getNode().getId());
    }

    @Test
    @DisplayName("모서리에 멈춘 말은 지름길로만 간다 (allowSkipShortcut=false)")
    void shortcutFromCorner() {
        move(0, 5, 5);
        List<BoardPath> paths = state.getMovePaths(state.getPiece(0), 1);
        assertEquals(1, paths.size());
        assertEquals(21, paths.get(0).destination().getId());
        assertTrue(state.tryMove(0, 1, 6).isEmpty(), "원래 길(6번)은 막혀야 함");
    }

    @Test
    @DisplayName("allowSkipShortcut=true면 모서리에서 원래 길도 고를 수 있다")
    void skipShortcutAllowed() {
        graph.setAllowSkipShortcut(true);
        move(0, 5, 5);
        assertEquals(2, state.getMovePaths(state.getPiece(0), 1).size());
        move(0, 1, 6);
    }

    @Test
    @DisplayName("모서리를 지나가기만 하면 지름길을 타지 않는다")
    void passingCornerKeepsOuterPath() {
        move(0, 4, 4);
        move(0, 5, 9);   // 5번을 지나 9번
    }

    @Test
    @DisplayName("대기 말은 빽도로 못 움직인다")
    void backDoFromWaiting() {
        assertTrue(state.getMovePaths(state.getPiece(0), -1).isEmpty());
    }

    @Test
    @DisplayName("1번 칸에서 빽도 → 참먹이(20번)")
    void backDoToEnd() {
        move(0, 1, 1);
        MoveResult result = move(0, -1, 20);
        assertTrue(result.path().backward());
        assertTrue(state.getPiece(0).isOnBoard());
    }

    @Test
    @DisplayName("바깥길을 한 바퀴 돌아 참먹이를 지나면 완주")
    void finishAroundOuter() {
        move(0, 4, 4);
        move(0, 5, 9);
        move(0, 5, 14);
        move(0, 5, 19);
        MoveResult result = move(0, 2, 0);   // 20(참먹이) → 0(출발) 들어오면 골인
        assertTrue(result.finished());
        assertTrue(state.getPiece(0).isFinished());
        assertFalse(state.teamFinished(0), "말 1이 남아 있음");
    }

    @Test
    @DisplayName("상대 말이 있는 칸에 도착하면 잡는다")
    void capture() {
        move(0, 3, 3);
        MoveResult result = move(100, 3, 3);
        assertEquals(1, result.capturedCount());
        assertTrue(state.getPiece(0).isWaiting());
        assertTrue(state.getPiece(100).isOnBoard());
    }

    @Test
    @DisplayName("같은 칸의 내 말은 업혀서 같이 움직인다")
    void stack() {
        move(0, 2, 2);
        move(1, 2, 2);
        MoveResult result = move(0, 1, 3);
        assertEquals(2, result.movedPieces().size());
        assertEquals(3, state.getPiece(1).getNode().getId());
    }

    @Test
    @DisplayName("갈 수 없는 칸이면 이동하지 않는다")
    void invalidDestination() {
        assertTrue(state.tryMove(0, 2, 5).isEmpty());
        assertTrue(state.tryMove(999, 1, 1).isEmpty(), "없는 말");
        assertTrue(state.getPiece(0).isWaiting());
    }

    @Test
    @DisplayName("hasAnyMove: 대기 말만 있을 때 빽도만 남으면 움직일 수 없다")
    void hasAnyMove() {
        assertFalse(state.hasAnyMove(0, List.of(-1)));
        assertTrue(state.hasAnyMove(0, List.of(-1, 2)));
    }
}
