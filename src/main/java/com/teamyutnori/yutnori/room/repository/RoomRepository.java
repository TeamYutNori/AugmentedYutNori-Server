package com.teamyutnori.yutnori.room.repository;

import com.teamyutnori.yutnori.room.model.Room;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RoomRepository {

    // roomCode를 key로 방 정보를 메모리에 저장
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    // 방 저장
    public Room save(Room room) {
        rooms.put(room.getRoomCode(), room);
        return room;
    }

    // roomCode로 방 조회
    public Optional<Room> findByRoomCode(String roomCode) {
        return Optional.ofNullable(rooms.get(roomCode));
    }

    // 모든 방 조회
    public Collection<Room> findAll() {
        return rooms.values();
    }

    // 방 삭제
    public void delete(String roomCode) {
        rooms.remove(roomCode);
    }

    // 방 존재 여부 확인
    public boolean exists(String roomCode) {
        return rooms.containsKey(roomCode);
    }
}