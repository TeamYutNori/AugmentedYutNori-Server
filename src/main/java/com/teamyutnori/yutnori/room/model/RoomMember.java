package com.teamyutnori.yutnori.room.model;

import lombok.Getter;

@Getter
public class RoomMember {
    // 게임 내부 플레이어 식별자
    private final String playerId;
    // 계정에 저장된 플레이어 닉네임
    private final String nickname;
    // 플레이어가 속한 팀
    private int team;
    // 게임 시작 준비 여부
    private boolean ready;

    public RoomMember(
            String playerId,
            String nickname
    ) {
        this.playerId = playerId;
        this.nickname = nickname;
        // 방 입장 직후에는 팀이 정해지지 않은 상태
        this.team = 0;
        // 방 입장 직후에는 준비되지 않은 상태
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