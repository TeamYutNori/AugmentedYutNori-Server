package com.teamyutnori.yutnori.game.augment.effect;

import com.teamyutnori.yutnori.game.board.BoardAugment;

// 이동 증강 공통 부모: 주인 팀만 들고, 4단계 훅은 BoardAugment 기본 구현(효과 없음)을 쓴다
// Unity Augment 추상 클래스와 같은 역할. 필요한 단계만 override 한다
public abstract class TeamAugment implements BoardAugment {

    private final int ownerTeam;

    protected TeamAugment(int ownerTeam) {
        this.ownerTeam = ownerTeam;
    }

    @Override
    public int ownerTeam() { return ownerTeam; }

    @Override
    public String toString() { return id() + "(Team" + ownerTeam + ")"; }
}
