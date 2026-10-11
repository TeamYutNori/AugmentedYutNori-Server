package com.teamyutnori.yutnori.game.augment.effect;

import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.AugmentContext;
import com.teamyutnori.yutnori.game.board.BoardPath;
import com.teamyutnori.yutnori.game.yut.YutResult;

import java.util.List;

// 청개구리: 빽도가 나오면 뒤로 1칸 대신 앞으로 1칸(도)으로 가는 길도 고를 수 있다 (Unity ContrarianAugment)
public class ContrarianAugment extends TeamAugment {

    public ContrarianAugment(int ownerTeam) { super(ownerTeam); }

    @Override
    public String id() { return AugmentIds.CONTRARIAN; }

    @Override
    public void modifyPaths(AugmentContext context, List<BoardPath> paths) {
        // 빽도가 아니거나 내 말이 아니면 아무것도 하지 않는다
        if (!context.isMine(this) || context.getRolledMoveCount() != YutResult.BackDo.steps()) return;

        // 앞으로 1칸(도)으로 가는 경로를 추가한다
        paths.addAll(context.getGraph().getMovePaths(
                context.getPiece().getNode(), context.getPiece().getCameFrom(), YutResult.Do.steps()));
    }
}
