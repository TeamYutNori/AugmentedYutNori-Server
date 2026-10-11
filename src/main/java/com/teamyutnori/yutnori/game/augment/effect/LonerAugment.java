package com.teamyutnori.yutnori.game.augment.effect;

import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.AugmentContext;
import com.teamyutnori.yutnori.game.board.BoardPath;
import com.teamyutnori.yutnori.game.board.Piece;

import java.util.List;

// 외톨이: 판 위에 내 말을 한 개만 둘 수 있는 대신(업기 불가) 상대에게 잡히지 않는다 (Unity LonerAugment)
public class LonerAugment extends TeamAugment {

    public LonerAugment(int ownerTeam) { super(ownerTeam); }

    @Override
    public String id() { return AugmentIds.LONER; }

    @Override
    public void modifyPaths(AugmentContext context, List<BoardPath> paths) {
        // 내 대기 말이 출발하는 이동만 본다
        if (!context.isMine(this) || !context.isWaiting()) return;

        // 판 위에 내 말이 이미 있으면 대기 말은 출발할 수 없다 (판에 한 개만)
        for (Piece piece : context.getState().getPieces()) {
            if (piece.getTeam() == ownerTeam() && piece.isOnBoard()) {
                paths.clear();
                return;
            }
        }
    }

    @Override
    public void modifyCaptures(AugmentContext context, List<Piece> captured) {
        // 내 이동이면 그대로 (상대는 잡을 수 있다)
        if (context.isMine(this)) return;
        // 상대 이동이면 잡힐 목록에서 내 말을 뺀다
        captured.removeIf(piece -> AugmentContext.isOwnedBy(this, piece));
    }
}
