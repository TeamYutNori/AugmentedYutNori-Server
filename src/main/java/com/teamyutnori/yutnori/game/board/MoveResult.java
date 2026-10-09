package com.teamyutnori.yutnori.game.board;

import java.util.List;

// 한 번의 이동 결과 (Unity MoveResult). TurnManager·MOVE_APPLIED에 넘길 값을 여기서 꺼낸다
public record MoveResult(
        Piece piece,                 // 선택한 말
        int moveCount,               // 굴린 칸 수 (증강 보정 전) = 사용한 윷 결과
        BoardPath path,
        List<Piece> movedPieces,     // 같이 움직인 말 (업힌 말 포함)
        List<Piece> capturedPieces   // 잡혀서 대기석으로 간 상대 말
) {
    public MoveResult {
        movedPieces = List.copyOf(movedPieces);
        capturedPieces = List.copyOf(capturedPieces);
    }

    public int capturedCount() { return capturedPieces.size(); }
    public boolean finished()  { return path.finished(); }
}
