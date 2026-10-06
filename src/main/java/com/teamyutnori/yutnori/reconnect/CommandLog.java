package com.teamyutnori.yutnori.reconnect;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/*
    방별로 서버가 확정한 명령을 순서대로 쌓는 로그
    seq는 1부터. 재접속 복원(readAfter)과 디버깅에 사용
    지금은 메모리 저장. (추후 Redis)
 */
@Component
public class CommandLog {

    public static  final int SYSTEM_TEAM = -1;

    /*
        @param type -> 메시지 타입
        @param team -> 명령을 만든 팀. 서버 명령이면 SYSTEM_TEAM
        @param payload -> 클라이언트에 다시 보낼 내용
     */
    public record  Entry(long seq, String type, int team, Object payload){}

    private final Map<String, List<Entry>> logs = new ConcurrentHashMap<>();

    public Entry append(String roomCode, String type, int team, Object payload){
        Objects.requireNonNull(roomCode, "roomCode");
        if(type == null || type.isBlank()){
            throw new IllegalArgumentException("type이 비어 있습니다");
        }

        List<Entry> log = logs.computeIfAbsent(roomCode, k -> new ArrayList<>());
        synchronized (log){
            Entry entry = new Entry(log.size() + 1L, type, team, payload);
            log.add(entry);
            return entry;
        }
    }

    public List<Entry> readAll(String roomCode){
        return readAfter(roomCode, 0);
    }

    // afterSeq보다 뒤의 명령 처리
    public List<Entry> readAfter(String roomCode, long afterSeq) {
        List<Entry> log = logs.get(roomCode);
        if(log == null) return List.of();
        synchronized (log){
            int from = (int) Math.min(Math.max(afterSeq, 0), log.size());
            return List.copyOf(log.subList(from, log.size()));
        }
    }

    // 마지막 seq
    public long lastSeq(String roomCode){
        List<Entry> log = logs.get(roomCode);
        if(log == null) return 0;
        synchronized (log){
            return log.size();
        }
    }

    // 게임 종료, 방 삭제 시 호출
    public void clear(String roomCode){
        logs.remove(roomCode);
    }
}
