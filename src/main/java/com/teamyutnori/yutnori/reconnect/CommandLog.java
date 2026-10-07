package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.ws.dto.MessageType;
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
        @param seq  -> 클라이언트에 보낸 WsEnvelope.seq
        @param type -> 메시지 타입
        @param team -> 명령을 만든 팀. 서버 명령이면 SYSTEM_TEAM
        @param payload -> 클라이언트에 다시 보낼 내용
     */
    public record  Entry(long seq, MessageType type, int team, Object payload){}

    private final Map<String, List<Entry>> logs = new ConcurrentHashMap<>();

    public Entry append(String roomCode, long seq, MessageType type, int team, Object payload){
        Objects.requireNonNull(roomCode, "roomCode");
        Objects.requireNonNull(type, "type");
        if(seq <= 0) throw new IllegalArgumentException("seq는 1 이상이어야 합니다. (현재 " + seq + ")");

        List<Entry> log = logs.computeIfAbsent(roomCode, k -> new ArrayList<>());
        synchronized (log){
            long last = log.isEmpty() ? 0 : log.get(log.size() -1).seq();
            if(seq <= last){
                throw new IllegalStateException("seq가 이전 기록보다 커야 합니다. (마지막 " + last + ", 현재 " + seq + ")");
            }
            Entry entry = new Entry(seq, type, team, payload);
            log.add(entry);
            return entry;
        }
    }

    public List<Entry> readAll(String roomCode){
        return readAfter(roomCode, 0);
    }

    // afterSeq보다 큰 seq만
    public List<Entry> readAfter(String roomCode, long afterSeq) {
        List<Entry> log = logs.get(roomCode);
        if(log == null) return List.of();
        synchronized (log){
            return log.stream().filter(entry -> entry.seq() > afterSeq).toList();
        }
    }

    // 마지막 seq
    public long lastSeq(String roomCode){
        List<Entry> log = logs.get(roomCode);
        if(log == null) return 0;
        synchronized (log){
            return log.isEmpty()? 0 : log.get(log.size() -1).seq();
        }
    }

    // 게임 종료, 방 삭제 시 호출
    public void clear(String roomCode){
        logs.remove(roomCode);
    }
}
