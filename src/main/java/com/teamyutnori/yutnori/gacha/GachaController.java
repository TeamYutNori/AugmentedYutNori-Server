package com.teamyutnori.yutnori.gacha;

import com.teamyutnori.yutnori.auth.TokenProvider;
import com.teamyutnori.yutnori.gacha.dto.GachaResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 프로필 아이콘 뽑기 API. 로그인 토큰(Authorization: Bearer {token}) 필요
//   POST /gacha/draw?count=10  뽑기 N회 → GachaResultResponse (count 생략 시 1회, 1·10만 허용. 그 외 400 INVALID_DRAW_COUNT)
//                              골드 부족 409 NOT_ENOUGH_GOLD (이때는 하나도 뽑지 않음)
// 보유 아이콘 조회는 도감(IconController), 프로필 아이콘 변경은 내 정보(PlayerController) 담당
@RestController
@RequiredArgsConstructor
@RequestMapping("/gacha")
public class GachaController {

    private final GachaService gachaService;
    private final TokenProvider tokenProvider;

    @PostMapping("/draw")
    public ResponseEntity<GachaResultResponse> draw(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "1") int count
    ) {
        return ResponseEntity.ok(gachaService.draw(playerId(authorization), count));
    }

    // "Bearer " 부분을 제외한 토큰으로 현재 로그인한 플레이어 확인 (PlayerController와 같은 방식)
    private String playerId(String authorization) {
        String token = authorization.replaceFirst("^Bearer ", "");
        return tokenProvider.getPlayerId(token);
    }
}
