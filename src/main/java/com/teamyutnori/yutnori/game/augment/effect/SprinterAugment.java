package com.teamyutnori.yutnori.game.augment.effect;

import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.AugmentContext;

// 스프린터: 새 말을 판에 올릴 때 이동 칸 수 +1 (Unity SprinterAugment)
public class SprinterAugment extends TeamAugment {

    public SprinterAugment(int ownerTeam) { super(ownerTeam); }

    @Override
    public String id() { return AugmentIds.SPRINTER; }

    @Override
    public int modifyMoveCount(AugmentContext context, int moveCount) {
        if (!context.isMine(this)) return moveCount;   // 내 말만
        if (!context.isWaiting()) return moveCount;    // 새 말을 판에 올릴 때만
        if (moveCount <= 0) return moveCount;          // 빽도 출발은 제외
        return moveCount + 1;
    }
}
