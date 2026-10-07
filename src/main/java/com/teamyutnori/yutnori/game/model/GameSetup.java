package com.teamyutnori.yutnori.game.model;

public record GameSetup(
        int teamCount,
        int piecesPerTeam,
        String boardLayoutId,
        boolean allowSkipShortcut
) {
    public static final int TEAM_ID_STRIDE = 100;   // Unity GameSetup.TeamIdStride와 동일

    // 생성할 때 값 검사 (Unity GameSetup.Validate와 같은 규칙)
    public GameSetup {
        if (teamCount < 2)
            throw new IllegalArgumentException("teamCount는 2 이상이어야 합니다: " + teamCount);
        if (piecesPerTeam < 1 || piecesPerTeam >= TEAM_ID_STRIDE)
            throw new IllegalArgumentException("piecesPerTeam은 1~99 사이여야 합니다: " + piecesPerTeam);
        if (boardLayoutId == null || boardLayoutId.isBlank())
            throw new IllegalArgumentException("boardLayoutId가 비어 있습니다");
    }

    // 말 ID 규칙: 팀 * 100 + 순번 (Unity GameSetup.PieceId와 동일)
    public static int pieceId(int team, int slot) { return team * TEAM_ID_STRIDE + slot; }
    public static int teamOf(int pieceId)         { return pieceId / TEAM_ID_STRIDE; }
}