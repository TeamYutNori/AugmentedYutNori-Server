package com.teamyutnori.yutnori.game.augment.effect;

import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.AugmentContext;
import com.teamyutnori.yutnori.game.yut.YutResult;

// 노장: 말이 하나 적은 대신 한 칸 덜 간다 (Unity VeteranAugment)
//  - 빽도 → 0칸 (갈 경로가 없어 쓸 수 없는 결과)
//  - 도   → 그대로 1칸
//  - 개~모 → 1칸 덜 (개 1, 걸 2, 윷 3, 모 4)
// 말 수(하나 적음)는 GameSession.piecesFor가 처리한다
public class VeteranAugment extends TeamAugment {

    public VeteranAugment(int ownerTeam) { super(ownerTeam); }

    @Override
    public String id() { return AugmentIds.VETERAN; }

    @Override
    public int modifyMoveCount(AugmentContext context, int moveCount) {
        if (!context.isMine(this)) return moveCount;   // 내 말만

        int rolled = context.getRolledMoveCount();
        if (rolled == YutResult.BackDo.steps()) return 0;      // 빽도는 움직이지 않는다
        if (rolled == YutResult.Do.steps()) return moveCount;  // 도는 그대로
        return moveCount - 1;                                  // 개~모는 1칸 덜
    }
}
