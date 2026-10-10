package com.teamyutnori.yutnori.game.model;

import com.teamyutnori.yutnori.config.GameProperties;

import java.util.ArrayList;
import java.util.List;

// 게임 한 판의 시작 설정 (Unity GameSetup과 같은 값·규칙)
// 판(BoardGraph)과 말(BoardState)은 이 값을 보고 GameService가 만든다
public record GameSetup(
        int teamCount,             // 팀 수 = 플레이어 수 (2~4)
        int piecesPerTeam,         // 팀당 말 개수
        String boardLayoutId,      // 판 종류 키: "default" → resources/boards/default.json
        boolean allowSkipShortcut  // 모서리·방에 멈췄을 때 지름길 말고 원래 길도 고를 수 있는지
) {
    public static final int TEAM_ID_STRIDE = 100;  // Unity GameSetup.TeamIdStride와 동일
    public static final int MAX_TEAMS = 4;         // 순위 보상이 4등까지라 4명 기준

    // 생성할 때 값 검사 (Unity GameSetup.Validate와 같은 규칙)
    public GameSetup {
        if (teamCount < 2 || teamCount > MAX_TEAMS)
            throw new IllegalArgumentException("teamCount는 2~" + MAX_TEAMS + " 사이여야 합니다: " + teamCount);
        if (piecesPerTeam < 1 || piecesPerTeam >= TEAM_ID_STRIDE)
            throw new IllegalArgumentException("piecesPerTeam은 1~99 사이여야 합니다: " + piecesPerTeam);
        if (boardLayoutId == null || boardLayoutId.isBlank())
            throw new IllegalArgumentException("boardLayoutId가 비어 있습니다");
    }

    // 방이 게임을 시작할 때 쓰는 기본 생성 (말 수는 서버 설정값 사용)
    public static GameSetup of(GameProperties props, int teamCount, String boardLayoutId) {
        return new GameSetup(teamCount, props.piecesPerTeam(), boardLayoutId, false);
    }

    // ── 말 ID 규칙: 팀 * 100 + 순번 (Unity PieceId / TeamOf / SlotOf와 동일) ──

    public static int pieceId(int team, int slot) { return team * TEAM_ID_STRIDE + slot; }
    public static int teamOf(int pieceId)         { return pieceId / TEAM_ID_STRIDE; }
    public static int slotOf(int pieceId)         { return pieceId % TEAM_ID_STRIDE; }

    // 이 게임에 실제로 있는 말 번호인지 (MOVE로 받은 pieceId 검사용)
    public boolean isValidPieceId(int pieceId) {
        int team = teamOf(pieceId);
        int slot = slotOf(pieceId);
        return pieceId >= 0 && team < teamCount && slot < piecesPerTeam;
    }

    // 한 팀의 말 번호 목록 (예: 팀 1, 말 2개 → [100, 101])
    public List<Integer> pieceIdsOf(int team) {
        List<Integer> ids = new ArrayList<>(piecesPerTeam);
        for (int slot = 0; slot < piecesPerTeam; slot++) ids.add(pieceId(team, slot));
        return ids;
    }

    // 전체 말 번호 목록. 팀 → 슬롯 순 (Unity EnumeratePieces와 같은 순서)
    // 서버와 클라가 같은 순서로 말을 만들어야 상태 해시가 같아진다
    public List<Integer> allPieceIds() {
        List<Integer> ids = new ArrayList<>(teamCount * piecesPerTeam);
        for (int team = 0; team < teamCount; team++) ids.addAll(pieceIdsOf(team));
        return ids;
    }

    public int totalPieces() { return teamCount * piecesPerTeam; }
}