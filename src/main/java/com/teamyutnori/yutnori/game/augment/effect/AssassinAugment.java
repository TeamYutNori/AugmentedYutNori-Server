package com.teamyutnori.yutnori.game.augment.effect;

import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.AugmentContext;
import com.teamyutnori.yutnori.game.board.BoardNode;
import com.teamyutnori.yutnori.game.board.Piece;

import java.util.ArrayList;
import java.util.List;

// 암살자: 도착 칸이 아니라 한 칸 앞(갈림길이면 다음 칸 전부)의 상대 말을 잡는다 (Unity AssassinAugment)
public class AssassinAugment extends TeamAugment {

    public AssassinAugment(int ownerTeam) { super(ownerTeam); }

    @Override
    public String id() { return AugmentIds.ASSASSIN; }

    // 평화주의자(0)보다 나중, 경호원(20)보다 먼저
    // → 평화주의자 팀도 잡을 수 있고, 경호원 팀의 업힌 말은 한 마리만 잡힌다
    @Override
    public int priority() { return 10; }

    @Override
    public void modifyCaptures(AugmentContext context, List<Piece> captured) {
        if (!context.isMine(this)) return;

        // 도착 칸의 일반 잡기는 할 수 없다
        captured.clear();

        // 잡기 단계에서는 말이 이미 도착 칸에 있다
        BoardNode destination = context.getPiece().getNode();

        // 한 칸 앞 칸들: 도착 칸의 지름길 칸들 + 바깥 길 다음 칸
        List<BoardNode> targets = new ArrayList<>(destination.getShortcutNodes());
        BoardNode next = destination.getNext(context.getPiece().getCameFrom());
        if (next != null && !targets.contains(next)) targets.add(next);

        for (BoardNode node : targets) {
            for (Piece piece : context.getState().getPiecesAt(node)) {
                if (piece.getTeam() != context.getTeam()) captured.add(piece);
            }
        }
    }
}
