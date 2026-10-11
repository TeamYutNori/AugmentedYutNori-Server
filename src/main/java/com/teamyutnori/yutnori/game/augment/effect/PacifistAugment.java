package com.teamyutnori.yutnori.game.augment.effect;

import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.AugmentContext;
import com.teamyutnori.yutnori.game.board.Piece;

import java.util.List;

// 평화주의자: 잡히지 않는 대신 상대 말을 잡을 수 없다 (Unity PacifistAugment, 우선순위 0)
public class PacifistAugment extends TeamAugment {

    public PacifistAugment(int ownerTeam) { super(ownerTeam); }

    @Override
    public String id() { return AugmentIds.PACIFIST; }

    @Override
    public void modifyCaptures(AugmentContext context, List<Piece> captured) {
        // 내 이동이면 아무것도 잡지 못한다
        if (context.isMine(this)) {
            captured.clear();
            return;
        }
        // 상대 이동이면 내 말은 잡히지 않는다
        captured.removeIf(piece -> AugmentContext.isOwnedBy(this, piece));
    }
}
