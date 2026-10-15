package com.teamyutnori.yutnori.room.service;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.common.NotFoundException;
import com.teamyutnori.yutnori.common.ForbiddenException;
import com.teamyutnori.yutnori.game.service.GameService;
import com.teamyutnori.yutnori.room.dto.CreateRoomRequest;
import com.teamyutnori.yutnori.room.dto.RoomSummaryResponse;
import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.model.RoomMember;
import com.teamyutnori.yutnori.room.model.RoomStatus;
import com.teamyutnori.yutnori.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomRepository roomRepository;
    private final GameService gameService;

    // 새로운 방을 생성하고 메모리 저장소에 등록
    public RoomSummaryResponse createRoom(CreateRoomRequest request) {
        Room room;

        // 혹시 같은 방 코드가 생성되면 충돌하지 않도록 다시 생성
        do {
            String roomCode = createRoomCode();
            room = new Room(
                    roomCode,
                    request.isPublic(),
                    request.maxPlayers(),
                    request.timeLimit()
            );
        } while (!roomRepository.saveIfAbsent(room));
        // 외부 응답용 DTO로 변환해서 반환
        return RoomSummaryResponse.from(room);
    }

    // 방 코드로 현재 방 정보를 조회
    public RoomSummaryResponse getRoom(String roomCode) {
        Room room = getRoomOrThrow(roomCode);
        return RoomSummaryResponse.from(room);
    }

    // 플레이어를 방에 참가시킴
    public Room joinRoom(
            String roomCode,
            String playerId,
            String nickname,
            String iconId
    ) {
        Room room = getRoomOrThrow(roomCode);
        // 같은 방에 여러 요청이 동시에 들어와도 참가자 상태가 꼬이지 않도록 동기화
        synchronized (room) {

            // 게임 시작 전인 방에만 새 플레이어가 참가 가능
            if (room.getStatus() != RoomStatus.WAITING) {
                throw new ConflictException(
                        "ROOM_NOT_WAITING",
                        "이미 게임이 시작된 방입니다."
                );
            }

            // 이미 참가한 플레이어라면 중복 추가하지 않음
            if (room.containsMember(playerId)) {
                return room;
            }

            // 최대 인원을 넘으면 참가 거부
            if (room.isFull()) {
                throw new ConflictException(
                        "ROOM_FULL",
                        "방 인원이 가득 찼습니다."
                );
            }

            RoomMember member = new RoomMember(
                    playerId,
                    nickname,
                    iconId
            );

            // 현재 아무도 사용하지 않는 가장 작은 팀 번호를 배정
            member.assignTeam(findAvailableTeam(room));
            // 검증이 모두 끝난 뒤 실제 참가자 목록에 추가
            room.addMember(member);
            return room;
        }
    }

    // 특정 플레이어의 준비 상태를 변경
    public Room changeReady(
            String roomCode,
            String playerId,
            boolean ready
    ) {
        Room room = getRoomOrThrow(roomCode);
        // 준비 상태 변경도 같은 방 요청끼리 충돌하지 않도록 동기화
        synchronized (room) {
            RoomMember member = room.findMember(playerId);
            // 방에 없는 플레이어는 준비 상태를 변경할 수 없음
            if (member == null) {
                throw new NotFoundException(
                        "ROOM_MEMBER_NOT_FOUND",
                        "방에 참가하지 않은 플레이어입니다."
                );
            }
            // 클라이언트에서 전달한 준비 상태로 변경
            member.setReady(ready);
            return room;
        }
    }

    // 방장의 게임 시작 요청 처리
    public Room requestGameStart(
            String roomCode,
            String requesterId,
            Predicate<String> isConnected
    ) {
        Room room = getRoomOrThrow(roomCode);

        // 같은 방에 여러 요청이 동시에 들어와도 참가자 상태가 꼬이지 않도록 동기화
        synchronized (room) {

            // 방장만 시작 가능
            if (!room.isHost(requesterId)) {
                throw new ForbiddenException(
                        "ONLY_HOST_CAN_START",
                        "방장만 게임을 시작할 수 있습니다."
                );
            }

            // 대기 중인 방만 시작 가능
            if (room.getStatus() != RoomStatus.WAITING) {
                throw new ConflictException(
                        "ROOM_ALREADY_STARTED",
                        "이미 게임이 시작된 방입니다."
                );
            }

            // 게임은 2 ~ 4명일 때만 시작 가능
            int playerCount = room.getCurrentPlayers();
            if (playerCount < 2 || playerCount > 4) {
                throw new ConflictException(
                        "INVALID_PLAYER_COUNT",
                        "게임은 2명에서 4명까지 시작할 수 있습니다."
                );
            }

            // 참가자 전원이 준비 상태여야 함
            if (!room.isAllReady()) {
                throw new ConflictException(
                        "PLAYERS_NOT_READY",
                        "모든 참가자가 준비해야 합니다."
                );
            }

            // 참가자 전원이 WebSocket에 연결되어 있어야 함
            boolean allConnected = room.getMembers()
                    .stream()
                    .allMatch(member -> isConnected.test(member.getPlayerId())
                    );

            if (!allConnected) {
                throw new ConflictException(
                        "PLAYER_DISCONNECTED",
                        "연결이 끊긴 참가자가 있습니다."
                );
            }

            // 퇴장으로 비어 있는 팀 번호가 있다면 0 ~ N-1로 다시 정리
            room.normalizeTeams();
            // 중복 요청이 다시 통과하지 못하도록 PLAYING으로 변경
            room.startGame();
            return room;
        }
    }

    // GameService에 전달하기 위한 playerId → team 정보 생성
    public Map<String, Integer> getPlayerTeams(String roomCode) {
        Room room = getRoomOrThrow(roomCode);
        synchronized (room) {
            return room.getMembers()
                    .stream()
                    .collect(Collectors.toMap(
                            RoomMember::getPlayerId,
                            RoomMember::getTeam
                    ));
        }
    }

    // 플레이어를 방에서 퇴장시킴
    public Room leaveRoom(
            String roomCode,
            String playerId
    ) {
        Room room = getRoomOrThrow(roomCode);

        synchronized (room) {
            // 실제 참가 중인 플레이어만 퇴장 가능
            if (!room.containsMember(playerId)) {
                throw new NotFoundException(
                        "ROOM_MEMBER_NOT_FOUND",
                        "방에 참가하지 않은 플레이어입니다."
                );
            }
            room.removeMember(playerId);

            // 마지막 플레이어가 나가면 빈 방은 저장소에서 제거
            if (room.getCurrentPlayers() == 0) {
                roomRepository.delete(roomCode);
                // 방이 완전히 비면 게임 세션도 같이 정리
                gameService.removeGame(roomCode);
            }
            return room;
        }
    }

    public Room resetAfterGame(String roomCode) {
        Room room = getRoomOrThrow(roomCode);
        synchronized (room) {
            room.resetForRematch();
            return room;
        }
    }

    // 현재 아무도 사용하지 않는 가장 작은 팀 번호를 찾음
    private int findAvailableTeam(Room room) {
        for (int team = 0; team < room.getMaxPlayers(); team++) {
            final int candidate = team;
            boolean alreadyUsed = room.getMembers()
                    .stream()
                    .anyMatch(member -> member.getTeam() == candidate);

            // 아무도 사용하지 않는 팀 번호를 찾으면 바로 반환
            if (!alreadyUsed) {
                return candidate;
            }
        }

        // 정상적으로는 room.isFull()에서 먼저 막히기 때문에 발생하지 않음
        throw new ConflictException(
                "NO_AVAILABLE_TEAM",
                "배정 가능한 팀이 없습니다."
        );
    }

    // 방 조회 로직을 한 곳에서 처리
    public Room getRoomOrThrow(String roomCode) {
        return roomRepository.findByCode(roomCode)
                .orElseThrow(() -> new NotFoundException(
                        "ROOM_NOT_FOUND",
                        "존재하지 않는 방입니다."
                ));
    }

    // 외부에서 사용하기 쉬운 6자리 대문자 방 코드 생성
    private String createRoomCode() {
        return UUID.randomUUID()
                .toString()
                .substring(0, 6)
                .toUpperCase();
    }
}