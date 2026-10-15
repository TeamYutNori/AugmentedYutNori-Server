package com.teamyutnori.yutnori.game.augment.effect;

import com.teamyutnori.yutnori.game.augment.AugmentIds;
import com.teamyutnori.yutnori.game.board.AugmentContext;
import com.teamyutnori.yutnori.game.board.Piece;

import java.util.List;

// 경호원: 업힌 내 말이 잡혀도 한 마리만 대기석으로 돌아간다 (Unity FriendShieldAugment)
public class FriendShieldAugment extends TeamAugment {

    public FriendShieldAugment(int ownerTeam) { super(ownerTeam); }

    @Override
    public String id() { return AugmentIds.FRIEND_SHIELD; }

    // 암살자(10)가 잡을 말을 넣은 뒤에도 업힌 말 중 한 마리만 잡히도록 마지막에 실행
    @Override
    public int priority() { return 20; }

    @Override
    public void modifyCaptures(AugmentContext context, List<Piece> captured) {
        // 잡힐 말 중 이 증강 주인 팀의 말
        List<Piece> mine = captured.stream().filter(piece -> AugmentContext.isOwnedBy(this, piece)).toList();

        // 업힌 말이 아니면 그대로
        if (mine.size() < 2) return;

        // 마지막 말만 잡히도록 나머지를 목록에서 뺀다
        for (int i = 0; i < mine.size() - 1; i++) {
            captured.remove(mine.get(i));
        }
    }
}
