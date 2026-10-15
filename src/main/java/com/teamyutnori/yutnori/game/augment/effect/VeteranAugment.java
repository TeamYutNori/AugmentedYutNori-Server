package com.teamyutnori.yutnori.game.augment.effect;

import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.AugmentContext;
import com.teamyutnori.yutnori.game.yut.YutResult;

// 노장: 말이 하나 적고, 멀리 갈 때(윷·모) 다리가 아파 한 칸 덜 간다 (Unity VeteranAugment)
//  - 빽도·도·개·걸 → 그대로
//  - 윷 → 3칸, 모 → 4칸
// 말 수(하나 적음)는 GameSession.piecesFor가 처리한다
public class VeteranAugment extends TeamAugment {

    public VeteranAugment(int ownerTeam) { super(ownerTeam); }

    @Override
    public String id() { return AugmentIds.VETERAN; }

    @Override
    public int modifyMoveCount(AugmentContext context, int moveCount) {
        if (!context.isMine(this)) return moveCount;   // 내 말만

        int rolled = context.getRolledMoveCount();
        boolean far = rolled == YutResult.Yut.steps() || rolled == YutResult.Mo.steps();
        return far ? moveCount - 1 : moveCount;        // 윷·모만 1칸 덜
    }
}
