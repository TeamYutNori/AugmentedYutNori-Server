package com.teamyutnori.yutnori.room.model;

import lombok.Getter;

@Getter
public class RoomMember {

    // 외부에서 사용하는 플레이어 식별자(UUID)
    private final String playerId;

    // 플레이어 닉네임
    private final String nickname;

    // 배정된 팀 번호
    private final int team;

    // 준비 상태
    private boolean ready;

    public RoomMember(String playerId, String nickname, int team) {
        this.playerId = playerId;
        this.nickname = nickname;
        this.team = team;
        this.ready = false;
    }

    // 준비 상태 변경
    public void setReady(boolean ready) {
        this.ready = ready;
    }
}