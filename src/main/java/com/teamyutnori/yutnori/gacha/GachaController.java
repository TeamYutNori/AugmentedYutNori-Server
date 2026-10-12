package com.teamyutnori.yutnori.gacha;

import com.teamyutnori.yutnori.auth.TokenProvider;
import com.teamyutnori.yutnori.gacha.dto.GachaResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 프로필 아이콘 뽑기 API. 로그인 토큰(Authorization: Bearer {token}) 필요
//   POST /gacha/draw  뽑기 1회 → GachaResultResponse (골드 부족 409 NOT_ENOUGH_GOLD)
// 보유 아이콘 조회는 도감(IconController), 프로필 아이콘 변경은 내 정보(PlayerController) 담당
@RestController
@RequiredArgsConstructor
@RequestMapping("/gacha")
public class GachaController {

    private final GachaService gachaService;
    private final TokenProvider tokenProvider;

    @PostMapping("/draw")
    public ResponseEntity<GachaResultResponse> draw(
            @RequestHeader("Authorization") String authorization
    ) {
        return ResponseEntity.ok(gachaService.draw(playerId(authorization)));
    }

    // "Bearer " 부분을 제외한 토큰으로 현재 로그인한 플레이어 확인 (PlayerController와 같은 방식)
    private String playerId(String authorization) {
        String token = authorization.replaceFirst("^Bearer ", "");
        return tokenProvider.getPlayerId(token);
    }
}
