package com.teamyutnori.yutnori.room.service;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.room.dto.CreateRoomRequest;
import com.teamyutnori.yutnori.room.dto.RoomSummaryResponse;
import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.model.RoomMember;
import com.teamyutnori.yutnori.room.model.RoomStatus;
import com.teamyutnori.yutnori.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    // 방 생성
    public RoomSummaryResponse createRoom(CreateRoomRequest request) {
        String roomCode = generateRoomCode();

        Room room = new Room(
                roomCode,
                request.roomName(),
                request.maxPlayers()
        );

        roomRepository.save(room);

        return toSummary(room);
    }

    // 전체 방 목록 조회
    public List<RoomSummaryResponse> getRooms() {
        return roomRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    // 방 하나 조회
    public Room getRoom(String roomCode) {
        return roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new NotFoundException(
                        "ROOM_NOT_FOUND",
                        "방을 찾을 수 없습니다."
                ));
    }

    // 방 참가
    public Room joinRoom(String roomCode, String playerId, String nickname) {
        Room room = getRoom(roomCode);

        synchronized (room) {
            if (room.getStatus() != RoomStatus.WAITING) {
                throw new ConflictException(
                        "ROOM_ALREADY_STARTED",
                        "이미 게임이 시작된 방입니다."
                );
            }

            if (room.findMember(playerId) != null) {
                throw new ConflictException(
                        "PLAYER_ALREADY_JOINED",
                        "이미 참가한 플레이어입니다."
                );
            }

            if (room.isFull()) {
                throw new ConflictException(
                        "ROOM_FULL",
                        "방이 가득 찼습니다."
                );
            }

            // 현재 인원 순서대로 팀 번호 배정
            int team = room.getMembers().size();

            room.addMember(
                    new RoomMember(playerId, nickname, team)
            );

            return room;
        }
    }

    // 준비 상태 변경
    public Room setReady(String roomCode, String playerId, boolean ready) {
        Room room = getRoom(roomCode);

        synchronized (room) {
            RoomMember member = room.findMember(playerId);

            if (member == null) {
                throw new NotFoundException(
                        "PLAYER_NOT_IN_ROOM",
                        "방에 참가하지 않은 플레이어입니다."
                );
            }

            member.setReady(ready);

            return room;
        }
    }

    // 방 나가기
    public Room leaveRoom(String roomCode, String playerId) {
        Room room = getRoom(roomCode);

        synchronized (room) {
            RoomMember member = room.findMember(playerId);

            if (member == null) {
                throw new NotFoundException(
                        "PLAYER_NOT_IN_ROOM",
                        "방에 참가하지 않은 플레이어입니다."
                );
            }

            room.removeMember(playerId);

            return room;
        }
    }

    // 응답 DTO 변환
    private RoomSummaryResponse toSummary(Room room) {
        return new RoomSummaryResponse(
                room.getRoomCode(),
                room.getRoomName(),
                room.getMembers().size(),
                room.getMaxPlayers(),
                room.getStatus()
        );
    }

    // 6자리 방 코드 생성
    private String generateRoomCode() {
        String roomCode;

        do {
            roomCode = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 6)
                    .toUpperCase();
        } while (roomRepository.exists(roomCode));

        return roomCode;
    }
}