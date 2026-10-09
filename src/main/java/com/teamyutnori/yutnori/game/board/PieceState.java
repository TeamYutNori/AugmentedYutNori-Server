package com.teamyutnori.yutnori.game.board;

// 말 상태 (Unity PieceState)
public enum PieceState {
    WAITING,   // 대기석 (판에 안 나옴)
    ON_BOARD,  // 판 위
    FINISHED   // 완주
}
