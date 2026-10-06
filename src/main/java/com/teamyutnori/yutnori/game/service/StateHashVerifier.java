package com.teamyutnori.yutnori.game.service;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/*
    클라이언트들이 보낸 해시 상태 해시를 seq별로 모아 비교
    서버는 모두 같은 지만 확인
    각 플레이어가 같은 seq에 대해 submit -> 모두 모이면 MATCH 또는 DESYNC
    게임이 끝나면 clearRoom으로 정리
*/
@Component
public class StateHashVerifier {

    private static final Pattern HASH_FORMAT = Pattern.compile("^[0-9a-f]{16}$");

    public enum Status{ PENDING, MATCH, DESYNC }

    // 비교 결과. hashes는 playerId -> stateHash (DESYNC 로그용)
    public record  Result(String roomCode, long seq, Status status, Map<String, String> hashes){
        public Result{
            hashes = Collections.unmodifiableMap(new LinkedHashMap<>(hashes));
        }
    }

    private static class RoomHashes{
        final Map<Long, Map<String, String>> pending = new HashMap<>();
        final Map<Long, Result> resolved = new HashMap<>();
    }

    private final Map<String, RoomHashes> rooms = new ConcurrentHashMap <>();

    /*
        @param expectedPlayers가 이 seq에 해시를 보내야 하는 인원
        @return 아직 덜 모였으면 PENDING, 다 모였으면 MATH / DESYNC
        VV
    */
    public Result submit(String roomCode, long seq, String playerId, String stateHash, int expectedPlayers){
        Objects.requireNonNull(roomCode, "roomCode");
        Objects.requireNonNull(playerId, "playerId");
        if(expectedPlayers < 2) throw new IllegalArgumentException("expectedPlayers는 2 이상이어야 합니다.(현재 " + expectedPlayers + ")");

        String hash = normalize(stateHash);

        RoomHashes room = rooms.computeIfAbsent(roomCode, k -> new RoomHashes());
        synchronized (room){
            Result done = room.resolved.get(seq);
            if(done != null) return done;

            Map<String, String> hashes = room.pending.computeIfAbsent(seq, k -> new LinkedHashMap<>());
            String previous = hashes.get(playerId);
            if(previous != null && !previous.equals(hash)){
                throw new IllegalStateException("플레이어 " + playerId + "가 seq" + seq + "에에 다른 해시를 다시 보냈습니다. (" + previous + " → " + hash + ")");

            }
            hashes.put(playerId, hash);

            if(hashes.size() < expectedPlayers) return new Result(roomCode, seq, Status.PENDING, hashes);

            Status status = new HashSet<>(hashes.values()).size() == 1 ? Status.MATCH : Status.DESYNC;
            Result result = new Result(roomCode, seq, status, hashes);
            room.pending.remove(seq);
            room.resolved.put(seq, result);
            return result;
        }
    }

    // 게임 종료, 방 삭제 시 호출
    public void clearRoom(String roomCode){
        rooms.remove(roomCode);
    }

    // 아직 다 모이지 않은 seq 개수
    public int pendingCount(String roomCode){
        RoomHashes room = rooms.get(roomCode);
        if(room == null) return 0;
        synchronized (room) {
            return room.pending.size();
        }
    }

    private static String normalize(String stateHash){
        if(stateHash == null) throw new IllegalArgumentException("stateHash가 비어있습니다.");
        String hash = stateHash.trim().toLowerCase();
        if(!HASH_FORMAT.matcher(hash).matches()){
            throw new IllegalArgumentException("stateHash는 16자리 hex 문자열이어야 합니다. (현재 '" + stateHash + "')");
        }
        return hash;
    }
}
