package com.teamyutnori.yutnori.room.controller;

import com.teamyutnori.yutnori.room.dto.CreateRoomRequest;
import com.teamyutnori.yutnori.room.dto.RoomSummaryResponse;
import com.teamyutnori.yutnori.room.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms")
public class RoomController {
    private final RoomService roomService;

    // 새로운 방 생성
    @PostMapping
    public ResponseEntity<RoomSummaryResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request
    ) {
        RoomSummaryResponse response =
                roomService.createRoom(request);
        return ResponseEntity.ok(response);
    }

    // 방 코드로 방 정보 조회
    @GetMapping("/{code}")
    public ResponseEntity<RoomSummaryResponse> getRoom(
            @PathVariable String code
    ) {
        RoomSummaryResponse response =
                roomService.getRoom(code);
        return ResponseEntity.ok(response);
    }
}