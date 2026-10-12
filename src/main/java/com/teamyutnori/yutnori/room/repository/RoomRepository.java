package com.teamyutnori.yutnori.room.repository;

import com.teamyutnori.yutnori.room.model.Room;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RoomRepository {
    // roomCode → Room
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    // 방 저장
    public Room save(Room room) {
        rooms.put(room.getCode(), room);
        return room;
    }

    // 같은 코드가 없을 때만 저장
    public boolean saveIfAbsent(Room room) {
        return rooms.putIfAbsent(room.getCode(), room) == null;
    }

    // 방 코드로 조회
    public Optional<Room> findByCode(String code) {
        return Optional.ofNullable(rooms.get(code));
    }

    // 방 삭제
    public void delete(String code) {
        rooms.remove(code);
    }

    // 방 존재 여부
    public boolean exists(String code) {
        return rooms.containsKey(code);
    }
}