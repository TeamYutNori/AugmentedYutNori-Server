package com.teamyutnori.yutnori.room.model;

import lombok.Getter;

@Getter
public class RoomMember {
    // 게임 내부 플레이어 식별자
    private final String playerId;
    // 계정에 저장된 플레이어 닉네임
    private final String nickname;
    // 방 참가자의 프로필 아이콘 id
    private final String iconId;
    // 플레이어가 속한 팀
    private int team;
    // 게임 시작 준비 여부
    private boolean ready;

    public RoomMember(
            String playerId,
            String nickname,
            String iconId
    ) {
        this.playerId = playerId;
        this.nickname = nickname;
        this.iconId = iconId;
        this.team = 0;
        this.ready = false;
    }

    // 팀 배정
    public void assignTeam(int team) {
        this.team = team;
    }

    // 준비 상태 변경
    public void setReady(boolean ready) {
        this.ready = ready;
    }
}