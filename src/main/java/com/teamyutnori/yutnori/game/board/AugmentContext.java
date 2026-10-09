package com.teamyutnori.yutnori.game.board;

import java.util.List;

// 증강이 판단에 쓰는 이번 이동의 정보 (Unity AugmentContext)
public class AugmentContext {

    private final BoardState state;
    private final Piece piece;               // 선택한 말
    private final List<Piece> stack;         // 같이 움직이는 말 (업힌 말 포함, 판 밖 말은 자기 혼자)
    private final int rolledMoveCount;       // 굴린 칸 수 (증강 보정 전)
    private int moveCount;                   // 실제로 움직일 칸 수 (modifyMoveCount 단계가 끝난 뒤 확정)

    AugmentContext(BoardState state, Piece piece, List<Piece> stack, int rolledMoveCount) {
        this.state = state;
        this.piece = piece;
        this.stack = List.copyOf(stack);
        this.rolledMoveCount = rolledMoveCount;
        this.moveCount = rolledMoveCount;
    }

    public BoardState getState()     { return state; }
    public BoardGraph getGraph()     { return state.getGraph(); }
    public Piece getPiece()          { return piece; }
    public int getTeam()             { return piece.getTeam(); }
    public List<Piece> getStack()    { return stack; }
    public int getRolledMoveCount()  { return rolledMoveCount; }
    public int getMoveCount()        { return moveCount; }
    void setMoveCount(int moveCount) { this.moveCount = moveCount; }

    public boolean isWaiting()  { return piece.isWaiting(); }   // 판에 안 나온 말이 출발하는 이동인지
    public boolean isBackward() { return moveCount < 0; }       // 뒤로 가는 이동인지
    public boolean isStacked()  { return stack.size() >= 2; }   // 업힌 말이 같이 움직이는지

    // 움직이는 말이 이 증강 주인의 말인지
    public boolean isMine(BoardAugment augment) { return augment.ownerTeam() == getTeam(); }

    // piece가 이 증강 주인의 말인지 (방패처럼 상대 이동 중에 내 말을 지키는 증강에서 사용)
    public static boolean isOwnedBy(BoardAugment augment, Piece piece) {
        return piece != null && augment.ownerTeam() == piece.getTeam();
    }
}
