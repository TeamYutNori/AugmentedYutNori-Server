package com.teamyutnori.yutnori.player.controller;

import com.teamyutnori.yutnori.auth.TokenProvider;
import com.teamyutnori.yutnori.player.dto.ChangeNicknameRequest;
import com.teamyutnori.yutnori.player.dto.CreatePlayerRequest;
import com.teamyutnori.yutnori.player.dto.CreatedPlayerResponse;
import com.teamyutnori.yutnori.player.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/players")
public class PlayerController {
    private final PlayerService playerService;
    private final TokenProvider tokenProvider;

    // 게스트 플레이어 생성
    @PostMapping
    public ResponseEntity<CreatedPlayerResponse> createPlayer(
            @Valid @RequestBody CreatePlayerRequest request
    ) {
        CreatedPlayerResponse response = playerService.createPlayer(request);

        return ResponseEntity.ok(response);
    }

    // 현재 로그인한 플레이어의 닉네임 변경
    @PatchMapping("/me/nickname")
    public ResponseEntity<Void> changeNickname(
            // 로그인 시 발급받은 서버 토큰 사용
            @RequestHeader("Authorization") String authorization,
            // 요청 본문의 닉네임 유효성 검사
            @Valid @RequestBody ChangeNicknameRequest request
    ) {
        // "Bearer " 부분을 제외하고 실제 토큰만 추출
        String token = authorization.replaceFirst("^Bearer ", "");
        // 토큰으로 현재 로그인한 플레이어의 playerId 확인
        String playerId = tokenProvider.getPlayerId(token);
        // 실제 닉네임 변경 로직은 Service에서 처리
        playerService.changeNickname(playerId, request);
        // 정상적으로 변경되면 응답 본문 없이 204 반환
        return ResponseEntity.noContent().build();
    }
}