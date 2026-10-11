package com.teamyutnori.yutnori.game.augment.effect;

import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.AugmentContext;
import com.teamyutnori.yutnori.game.yut.YutResult;

// 수행자: 도·개가 나오면 1칸 더 (Unity MonkAugment)
public class MonkAugment extends TeamAugment {

    public MonkAugment(int ownerTeam) { super(ownerTeam); }

    @Override
    public String id() { return AugmentIds.MONK; }

    @Override
    public int modifyMoveCount(AugmentContext context, int moveCount) {
        if (!context.isMine(this)) return moveCount;
        int rolled = context.getRolledMoveCount();
        if (rolled != YutResult.Do.steps() && rolled != YutResult.Gae.steps()) return moveCount;
        return moveCount + 1;
    }
}
