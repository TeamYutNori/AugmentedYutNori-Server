package com.teamyutnori.yutnori.ws;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// 방마다 서버가 보내는 메시지 순번(seq)을 1부터 발급한다. 방이 실제로 삭제될 때만 remove (재접속 후에도 번호가 이어져야 함)
@Component
public class SequenceTracker {
    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public long next(String roomCode) {
        return sequences.computeIfAbsent(roomCode, k -> new AtomicLong()).incrementAndGet();
    }

    public void remove(String roomCode) {
        sequences.remove(roomCode);
    }

    public long current(String roomCode){
        AtomicLong seq = sequences.get(roomCode);
        return seq == null ? 0 : seq.get();
    }
}
