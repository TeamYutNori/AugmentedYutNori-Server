package com.teamyutnori.yutnori.room.controller;

import com.teamyutnori.yutnori.auth.TokenProvider;
import com.teamyutnori.yutnori.common.UnauthorizedException;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import com.teamyutnori.yutnori.room.dto.CreateRoomRequest;
import com.teamyutnori.yutnori.room.dto.RoomSummaryResponse;
import com.teamyutnori.yutnori.room.service.MatchmakingService;
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
    private final MatchmakingService matchmakingService;
    private final TokenProvider tokenProvider;
    private final PlayerRepository playerRepository;

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

    // 빠른 참가: 자리가 남은 방에 내 자리를 잡아 두고 방 코드를 준다 (JOIN_ROOM까지 유지)
    // Authorization 헤더는 선택. 있으면 그 플레이어 이름으로 자리를 잡고, 없거나 확인이 안 되면 익명 예약
    @PostMapping("/quick-join")
    public ResponseEntity<RoomSummaryResponse> quickJoin(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateRoomRequest request
    ) {
        return ResponseEntity.ok(
                matchmakingService.quickJoin(request, findPlayerId(authorization))
        );
    }

    // 서버 토큰 또는 게스트 토큰으로 playerId 찾기 (WebSocket 접속 인증과 같은 순서). 못 찾으면 null
    private String findPlayerId(String authorization) {
        if (authorization == null || authorization.isBlank()) return null;
        String token = authorization.replaceFirst("^Bearer ", "");
        try {
            return tokenProvider.getPlayerId(token);
        } catch (UnauthorizedException e) {
            return playerRepository.findByGuestToken(token)
                    .map(Player::getPlayerId)
                    .orElse(null);
        }
    }
}