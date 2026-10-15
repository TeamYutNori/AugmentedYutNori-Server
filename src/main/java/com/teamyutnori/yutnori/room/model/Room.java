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
    // 방 최소 인원
    private static final int MIN_PLAYERS = 2;
    // 제한 시간
    private final int timeLimit;
    // 현재 방 상태
    private RoomStatus status;
    // 현재 방에 참가한 플레이어 목록
    private final List<RoomMember> members;
    // 현재 방장 playerId
    private String hostPlayerId;

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
        this.hostPlayerId = null;
    }

    // 방에 플레이어 추가
    public void addMember(RoomMember member) {
        members.add(member);

        // 방에 처음 들어온 플레이어를 방장으로 지정
        if (hostPlayerId == null) {
            hostPlayerId = member.getPlayerId();
        }
    }

    // 방에서 플레이어 제거
    public void removeMember(String playerId) {
        members.removeIf(member -> member.getPlayerId().equals(playerId));

        // 방장이 나갔다면 남아 있는 첫 번째 플레이어에게 방장 권한 승계
        if (playerId.equals(hostPlayerId)) {
            hostPlayerId = members.isEmpty() ? null : members.get(0).getPlayerId();
        }
    }

    // 요청한 플레이어가 방장인지 확인
    public boolean isHost(String playerId) {
        return playerId != null && playerId.equals(hostPlayerId);
    }

    // 특정 플레이어가 방에 있는지 확인
    public boolean containsMember(String playerId) {
        return members.stream().anyMatch(member -> member.getPlayerId().equals(playerId));
    }

    // playerId로 방 참가자 찾기
    public RoomMember findMember(String playerId) {
        return members.stream().filter(member -> member.getPlayerId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    // 모든 참가자가 준비 상태인지 확인
    public boolean isAllReady() {
        return !members.isEmpty() && members.stream().allMatch(RoomMember::isReady);
    }

    // 현재 방 인원
    public int getCurrentPlayers() {
        return members.size();
    }

    // 게임 시작에 필요한 인원이 모두 들어왔는지 확인 (최소 2명)
    public boolean hasEnoughPlayers() { return members.size() >= MIN_PLAYERS; }

    // 방이 가득 찼는지 확인
    public boolean isFull() {
        return members.size() >= maxPlayers;
    }

    // 외부에서 참가자 목록 자체를 변경하지 못하도록 조회 전용으로 반환
    public List<RoomMember> getMembers() {
        return Collections.unmodifiableList(members);
    }

    // 게임 시작 직전 팀 번호를 0 ~ N-1로 다시 정리
    public void normalizeTeams() {
        for (int i = 0; i < members.size(); i++) {
            members.get(i).assignTeam(i);
        }
    }

    // 게임 시작 상태로 변경
    public void startGame() { this.status = RoomStatus.PLAYING; }

    // 재경기용 초기화 메서드
    public void resetForRematch() {
        this.status = RoomStatus.WAITING;
        members.forEach(member -> member.setReady(false));
    }

    // 게임 종료 상태로 변경
    public void finishGame() {
        this.status = RoomStatus.FINISHED;
    }
}