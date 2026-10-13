package com.teamyutnori.yutnori.room.service;

import com.teamyutnori.yutnori.room.dto.CreateRoomRequest;
import com.teamyutnori.yutnori.room.dto.RoomSummaryResponse;
import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchmakingService {

    private final RoomRepository roomRepository;
    private final RoomService roomService;

    // 참가 가능한 공개방을 찾고, 없으면 새 방 생성
    public RoomSummaryResponse quickJoin(CreateRoomRequest request) {

        Room room = roomRepository.findJoinableRoom()
                .orElse(null);

        // 참가할 수 있는 방이 있으면 해당 방 정보 반환
        if (room != null) {
            return RoomSummaryResponse.from(room);
        }

        // 참가 가능한 방이 없으면 새 공개방 생성
        CreateRoomRequest quickRoomRequest =
                new CreateRoomRequest(
                        true,
                        request.maxPlayers(),
                        request.timeLimit()
                );

        return roomService.createRoom(quickRoomRequest);
    }
}