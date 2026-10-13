package com.teamyutnori.yutnori.achievement.controller;

import com.teamyutnori.yutnori.achievement.AchievementService;
import com.teamyutnori.yutnori.achievement.dto.AchievementListResponse;
import com.teamyutnori.yutnori.achievement.dto.ClaimAchievementResponse;
import com.teamyutnori.yutnori.auth.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

// 업적 API. 로그인 토큰(Authorization: Bearer {token}) 필요
//   GET  /players/me/achievements             내 업적 목록 (진행도·달성·수령 여부)
//   POST /players/me/achievements/{id}/claim  보상 받기 → 골드 지급
//        아직 달성 안 함 409 ACHIEVEMENT_NOT_ACHIEVED / 이미 받음 409 ACHIEVEMENT_ALREADY_CLAIMED / 없는 id 404
@RestController
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService service;
    private final TokenProvider tokenProvider;

    @GetMapping("/players/me/achievements")
    public ResponseEntity<AchievementListResponse> list(
            @RequestHeader("Authorization") String authorization
    ) {
        return ResponseEntity.ok(service.list(playerId(authorization)));
    }

    @PostMapping("/players/me/achievements/{achievementId}/claim")
    public ResponseEntity<ClaimAchievementResponse> claim(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String achievementId
    ) {
        return ResponseEntity.ok(service.claim(playerId(authorization), achievementId));
    }

    private String playerId(String authorization) {
        String token = authorization.replaceFirst("^Bearer ", "");
        return tokenProvider.getPlayerId(token);
    }
}
