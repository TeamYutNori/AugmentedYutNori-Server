package com.teamyutnori.yutnori.player.controller;

import com.teamyutnori.yutnori.auth.TokenProvider;
import com.teamyutnori.yutnori.player.dto.ChangeIconRequest;
import com.teamyutnori.yutnori.player.dto.ChangeNicknameRequest;
import com.teamyutnori.yutnori.player.dto.CreatePlayerRequest;
import com.teamyutnori.yutnori.player.dto.CreatedPlayerResponse;
import com.teamyutnori.yutnori.player.dto.PlayerProfileResponse;
import com.teamyutnori.yutnori.player.service.AccountWithdrawalService;
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
    private final AccountWithdrawalService accountWithdrawalService;

    // Unity 에디터 테스트용 게스트 플레이어 생성
    @PostMapping
    public ResponseEntity<CreatedPlayerResponse> createPlayer(
            @Valid @RequestBody CreatePlayerRequest request
    ) {
        // 게스트 플레이어 생성 로직은 Service에서 처리
        CreatedPlayerResponse response = playerService.createPlayer(request);
        return ResponseEntity.ok(response);
    }

    // 현재 로그인한 플레이어의 프로필 정보 조회
    @GetMapping("/me")
    public ResponseEntity<PlayerProfileResponse> getMyProfile(
            @RequestHeader("Authorization") String authorization
    ) {
        // Authorization 헤더의 "Bearer " 부분을 제거하고 실제 서버 토큰만 추출
        String token = authorization.replaceFirst("^Bearer ", "");

        // 서버 토큰으로 현재 로그인한 플레이어의 playerId 조회
        String playerId = tokenProvider.getPlayerId(token);

        // 닉네임, 선택한 프로필 아이콘, 골드 정보를 조회
        PlayerProfileResponse response = playerService.getProfile(playerId);

        // 조회한 내 정보 반환
        return ResponseEntity.ok(response);
    }

    // 현재 로그인한 플레이어의 닉네임 변경
    @PatchMapping("/me/nickname")
    public ResponseEntity<Void> changeNickname(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ChangeNicknameRequest request
    ) {
        // Authorization 헤더에서 서버 토큰 추출
        String token = authorization.replaceFirst("^Bearer ", "");

        // 토큰을 이용해 현재 로그인한 플레이어 확인
        String playerId = tokenProvider.getPlayerId(token);

        // 실제 닉네임 중복 검사 및 변경은 Service에서 처리
        playerService.changeNickname(playerId, request);

        // 변경 성공 시 응답 본문 없이 204 반환
        return ResponseEntity.noContent().build();
    }

    // 현재 로그인한 플레이어의 프로필 아이콘 변경
    @PatchMapping("/me/icon")
    public ResponseEntity<Void> changeIcon(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ChangeIconRequest request
    ) {
        // Authorization 헤더에서 서버 토큰 추출
        String token = authorization.replaceFirst("^Bearer ", "");

        // 토큰을 이용해 현재 로그인한 플레이어 확인
        String playerId = tokenProvider.getPlayerId(token);

        // 선택한 프로필 아이콘을 Player에 저장
        // 아이콘 보유 여부 등의 검증은 Service에서 처리
        playerService.changeIcon(playerId, request);

        // 변경 성공 시 응답 본문 없이 204 반환
        return ResponseEntity.noContent().build();
    }

    // 회원 탈퇴: 계정 데이터(닉네임·골드·아이콘·업적)와 Firebase 계정을 완전히 삭제한다. 전적은 익명 통계로 유지
    // 방·게임에 참가 중이면 409 IN_ROOM. 같은 구글 계정으로 다시 로그인하면 새 계정이 만들어진다
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @RequestHeader("Authorization") String authorization
    ) {
        // Authorization 헤더에서 서버 토큰 추출
        String token = authorization.replaceFirst("^Bearer ", "");

        // 토큰을 이용해 현재 로그인한 플레이어 확인
        String playerId = tokenProvider.getPlayerId(token);

        accountWithdrawalService.withdraw(playerId);

        // 탈퇴 성공 시 응답 본문 없이 204 반환
        return ResponseEntity.noContent().build();
    }
}
