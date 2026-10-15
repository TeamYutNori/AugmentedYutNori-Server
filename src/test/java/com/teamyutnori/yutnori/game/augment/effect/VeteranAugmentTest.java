package com.teamyutnori.yutnori.game.augment.effect;

import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.BoardState;
import com.teamyutnori.yutnori.game.board.TestBoards;
import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.yut.YutResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 노장: Unity VeteranAugment와 같은 결과인지
//  - 이동: 빽도·도·개·걸 그대로, 윷·모만 1칸 덜
//  - 말 수: 기본 - 1, 마지막 슬롯 없음 / 나간 팀은 말 없음
// 기본 판: 0 출발 → 1, 2, 3 ...
class VeteranAugmentTest {

    // 팀 0이 노장, 말은 0번 하나만 써서 확인
    private BoardState veteranBoard() {
        BoardState state = new BoardState(TestBoards.defaultBoard(), List.of(0, 100));
        state.addAugment(new VeteranAugment(0));
        return state;
    }

    private int destinationOf(BoardState state, int pieceId, YutResult result) {
        return state.getMovePaths(state.getPiece(pieceId), result.steps()).get(0).destination().getId();
    }

    @Test
    @DisplayName("도·개·걸은 그대로, 윷·모만 1칸 덜")
    void moveCounts() {
        BoardState state = veteranBoard();
        assertEquals(1, destinationOf(state, 0, YutResult.Do));
        assertEquals(2, destinationOf(state, 0, YutResult.Gae));
        assertEquals(3, destinationOf(state, 0, YutResult.Geol));
        assertEquals(3, destinationOf(state, 0, YutResult.Yut));
        assertEquals(4, destinationOf(state, 0, YutResult.Mo));
    }

    @Test
    @DisplayName("빽도는 그대로 1칸 뒤로")
    void backDoNormal() {
        BoardState state = veteranBoard();
        assertTrue(state.tryMove(0, YutResult.Geol.steps(), 3).isPresent());   // 걸 → 3번
        assertEquals(2, destinationOf(state, 0, YutResult.BackDo));
    }

    @Test
    @DisplayName("다른 팀 말에는 영향 없음")
    void otherTeamUnaffected() {
        BoardState state = veteranBoard();
        assertEquals(4, destinationOf(state, 100, YutResult.Yut));
    }

    @Test
    @DisplayName("스프린터와 같이 있으면: 새 말 윷은 +1 -1로 그대로 4칸, 개는 +1만 3칸")
    void withSprinter() {
        BoardState state = veteranBoard();
        state.addAugment(new SprinterAugment(0));
        assertEquals(4, destinationOf(state, 0, YutResult.Yut));
        assertEquals(3, destinationOf(state, 0, YutResult.Gae));
    }

    // ── 말 수 (GameSession.createPieces) ──

    private GameSession newSession(int teamCount, int piecesPerTeam) {
        Map<String, Integer> players = new java.util.HashMap<>();
        for (int t = 0; t < teamCount; t++) players.put("p" + t, t);
        return new GameSession("ROOM", new GameSetup(teamCount, piecesPerTeam, "default", false),
                players, TestBoards.defaultBoard());
    }

    @Test
    @DisplayName("증강이 정해지기 전에는 말이 없다")
    void noPiecesBeforeAugments() {
        assertTrue(newSession(2, 4).getBoardState().getPieces().isEmpty());
    }

    @Test
    @DisplayName("노장 팀은 마지막 슬롯 없이 하나 적게 (4개 기준 100·101·102)")
    void veteranHasOneLess() {
        GameSession session = newSession(2, 4);
        session.addAugment(1, AugmentIds.VETERAN);
        session.createPieces();

        assertEquals(4, session.piecesFor(0));
        assertEquals(3, session.piecesFor(1));
        assertEquals(List.of(0, 1, 2, 3), idsOf(session, 0));
        assertEquals(List.of(100, 101, 102), idsOf(session, 1));
        assertNull(session.getBoardState().getPiece(103));
    }

    @Test
    @DisplayName("나간 팀(증강을 고른 뒤 나가도)은 말을 만들지 않는다")
    void removedTeamHasNoPieces() {
        GameSession session = newSession(3, 2);
        session.addAugment(2, AugmentIds.VETERAN);
        session.markRemoved(2);
        session.createPieces();

        assertEquals(List.of(), idsOf(session, 2));
        assertEquals(List.of(0, 1), idsOf(session, 0));
    }

    @Test
    @DisplayName("두 번 불러도 말이 늘어나지 않는다")
    void createPiecesIdempotent() {
        GameSession session = newSession(2, 2);
        session.createPieces();
        session.createPieces();
        assertEquals(4, session.getBoardState().getPieces().size());
    }

    private static List<Integer> idsOf(GameSession session, int team) {
        return session.getBoardState().getPiecesOf(team).stream().map(p -> p.getId()).sorted().toList();
    }
}
