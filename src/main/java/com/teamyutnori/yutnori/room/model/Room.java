package com.teamyutnori.yutnori.room.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class Room {
    // 클라이언트가 방을 찾을 때 사용하는 코드
    private final String code;
    // 방 공개 여부
    private final boolean isPublic;
    // 방 최대 인원
    private final int maxPlayers;
    // 제한 시간
    private final int timeLimit;
    // 현재 방 상태
    private RoomStatus status;
    // 현재 방에 참가한 플레이어 목록
    private final List<RoomMember> members;

    public Room(
            String code,
            boolean isPublic,
            int maxPlayers,
            int timeLimit
    ) {
        this.code = code;
        this.isPublic = isPublic;
        this.maxPlayers = maxPlayers;
        this.timeLimit = timeLimit;
        // 새로 생성된 방은 항상 대기 상태
        this.status = RoomStatus.WAITING;
        this.members = new ArrayList<>();
    }

    // 방에 플레이어 추가
    public void addMember(RoomMember member) {
        members.add(member);
    }

    // 방에서 플레이어 제거
    public void removeMember(String playerId) {
        members.removeIf(
                member -> member.getPlayerId().equals(playerId)
        );
    }

    // 특정 플레이어가 방에 있는지 확인
    public boolean containsMember(String playerId) {
        return members.stream()
                .anyMatch(
                        member -> member.getPlayerId().equals(playerId)
                );
    }

    // playerId로 방 참가자 찾기
    public RoomMember findMember(String playerId) {
        return members.stream()
                .filter(
                        member -> member.getPlayerId().equals(playerId)
                )
                .findFirst()
                .orElse(null);
    }

    // 현재 방 인원
    public int getCurrentPlayers() {
        return members.size();
    }

    // 방이 가득 찼는지 확인
    public boolean isFull() {
        return members.size() >= maxPlayers;
    }

    // 외부에서 참가자 목록 자체를 변경하지 못하도록 조회 전용으로 반환
    public List<RoomMember> getMembers() {
        return Collections.unmodifiableList(members);
    }

    // 게임 시작 상태로 변경
    public void startGame() {
        this.status = RoomStatus.PLAYING;
    }

    // 게임 종료 상태로 변경
    public void finishGame() {
        this.status = RoomStatus.FINISHED;
    }
}