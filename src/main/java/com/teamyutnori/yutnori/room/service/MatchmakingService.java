package com.teamyutnori.yutnori.room.service;

import com.teamyutnori.yutnori.room.dto.CreateRoomRequest;
import com.teamyutnori.yutnori.room.dto.RoomSummaryResponse;
import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.model.RoomStatus;
import com.teamyutnori.yutnori.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// 빠른 참가: 자리가 남은 공개 대기방을 골라 자리를 예약하고 방 코드를 돌려준다
//  - 방 고르기와 실제 참가(WebSocket JOIN_ROOM)가 나뉘어 있어서, 예약 없이 코드만 주면
//    동시에 몰린 사람들이 같은 방을 받고 늦게 온 사람은 ROOM_FULL로 거부된다 (부하 테스트에서 확인)
//  - 그래서 방을 잠근 상태에서 "참가자 + 예약 < 최대 인원"인지 보고 자리를 하나 잡아 둔다.
//    JOIN_ROOM이 오면 RoomService.joinRoom이 예약을 쓰고, RESERVATION_TTL 안에 안 오면 자리가 다시 풀린다
@Service
@RequiredArgsConstructor
public class MatchmakingService {

    static final Duration RESERVATION_TTL = Duration.ofSeconds(10);

    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final Clock clock;

    // 참가 가능한 공개방에 자리를 잡고, 없으면 새 방을 만들어 자리를 잡는다
    // playerId: 요청한 플레이어 (토큰이 없거나 확인이 안 되면 null → 누구 것인지 모르는 예약으로 잡음)
    public RoomSummaryResponse quickJoin(CreateRoomRequest request, String playerId) {
        Instant now = clock.instant();
        Instant reservedUntil = now.plus(RESERVATION_TTL);
        String key = playerId != null ? playerId : Room.ANONYMOUS_PREFIX + UUID.randomUUID();

        for (Room room : roomRepository.findWaitingPublicRooms()) {
            synchronized (room) {   // joinRoom과 같은 잠금. 확인과 예약 사이에 다른 요청이 끼지 않는다
                if (room.getStatus() != RoomStatus.WAITING) continue;
                // 목록을 받은 뒤 마지막 사람이 나가 방이 지워졌을 수 있다 (지우기도 같은 잠금 안에서 일어난다)
                if (roomRepository.findByCode(room.getCode()).orElse(null) != room) continue;
                // 이미 이 방에 들어가 있거나 자리를 잡아 둔 플레이어가 다시 누르면 같은 방을 준다
                if (playerId != null && room.containsMember(playerId)) {
                    return RoomSummaryResponse.from(room);
                }
                if (playerId != null && room.hasReservation(playerId, now)) {
                    room.reserveSeat(playerId, reservedUntil);   // 만료 시각만 늘림
                    return RoomSummaryResponse.from(room);
                }
                if (room.hasFreeSeat(now)) {
                    room.reserveSeat(key, reservedUntil);
                    return RoomSummaryResponse.from(room);
                }
            }
        }

        // 참가 가능한 방이 없으면 새 공개방 생성 (만든 사람 자리는 미리 예약)
        CreateRoomRequest quickRoomRequest =
                new CreateRoomRequest(
                        true,
                        request.maxPlayers(),
                        request.timeLimit()
                );
        return RoomSummaryResponse.from(roomService.createRoomWithReservation(quickRoomRequest, key, reservedUntil));
    }
}
