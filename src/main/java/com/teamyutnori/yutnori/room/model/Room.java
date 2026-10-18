package com.teamyutnori.yutnori.room.model;

import lombok.AccessLevel;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    // 빠른 참가로 잡아 둔 자리: 예약 키(playerId, 모르면 "anon:" + 임의값) → 만료 시각
    // 빠른 참가 응답 ~ JOIN_ROOM 사이에 다른 사람이 자리를 가져가지 않게 한다. 방 단위 synchronized 안에서만 다룬다
    @Getter(AccessLevel.NONE)
    private final Map<String, Instant> reservations = new LinkedHashMap<>();

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

    // ── 빠른 참가 자리 예약 ──

    public static final String ANONYMOUS_PREFIX = "anon:";

    // 빠른 참가로 자리 하나를 잡아 둔다. expiresAt까지 JOIN_ROOM이 오지 않으면 자리는 다시 풀린다
    // 같은 키로 다시 잡으면 만료 시각만 늘어난다 (자리를 두 개 차지하지 않음)
    public void reserveSeat(String key, Instant expiresAt) {
        reservations.put(key, expiresAt);
    }

    // 아직 유효한 예약 수 (만료된 예약은 이때 정리)
    public int reservedSeats(Instant now) {
        reservations.values().removeIf(expiresAt -> !expiresAt.isAfter(now));
        return reservations.size();
    }

    public boolean hasReservation(String key, Instant now) {
        reservedSeats(now);
        return reservations.containsKey(key);
    }

    // 참가자 + 예약을 합쳐도 자리가 남는지. 빠른 참가 방 고르기와 예약 없는 참가에 쓴다
    public boolean hasFreeSeat(Instant now) {
        return members.size() + reservedSeats(now) < maxPlayers;
    }

    // 참가할 때 자기 예약을 쓴다. 자기 예약이 있었으면 true
    public boolean takeReservation(String playerId, Instant now) {
        reservedSeats(now);
        return reservations.remove(playerId) != null;
    }

    // 누구 것인지 모르는 예약(토큰 없이 빠른 참가한 경우) 하나를 쓴다. 있었으면 true
    public boolean takeAnonymousReservation(Instant now) {
        reservedSeats(now);
        Iterator<String> keys = reservations.keySet().iterator();
        while (keys.hasNext()) {
            if (keys.next().startsWith(ANONYMOUS_PREFIX)) {
                keys.remove();
                return true;
            }
        }
        return false;
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