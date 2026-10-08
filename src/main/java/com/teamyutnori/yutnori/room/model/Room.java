package com.teamyutnori.yutnori.room.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class Room {
    // 방을 구분하는 코드
    private final String roomCode;
    // 방 이름
    private final String roomName;
    // 방 최대 인원
    private final int maxPlayers;
    // 현재 방 상태
    private RoomStatus status;
    // 방에 참가한 플레이어 목록
    private final List<RoomMember> members = new ArrayList<>();

    public Room(String roomCode, String roomName, int maxPlayers) {
        this.roomCode = roomCode;
        this.roomName = roomName;
        this.maxPlayers = maxPlayers;
        this.status = RoomStatus.WAITING;
    }

    // 방에 플레이어 추가
    public void addMember(RoomMember member) {
        members.add(member);
    }

    // 방에서 플레이어 제거
    public void removeMember(String playerId) {
        members.removeIf(member -> member.getPlayerId().equals(playerId));
    }

    // playerId로 방 참가자 조회
    public RoomMember findMember(String playerId) {
        return members.stream()
                .filter(member -> member.getPlayerId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    // 방이 가득 찼는지 확인
    public boolean isFull() {
        return members.size() >= maxPlayers;
    }

    // 모든 참가자가 준비됐는지 확인
    public boolean isAllReady() {
        return !members.isEmpty()
                && members.size() == maxPlayers
                && members.stream().allMatch(RoomMember::isReady);
    }

    // 게임 시작 상태로 변경
    public void startGame() {
        this.status = RoomStatus.PLAYING;
    }

    // 게임 종료 상태로 변경
    public void finishGame() {
        this.status = RoomStatus.FINISHED;
    }

    // 외부에서 참가자 목록을 직접 수정하지 못하도록 읽기 전용으로 반환
    public List<RoomMember> getMembers() {
        return Collections.unmodifiableList(members);
    }
}