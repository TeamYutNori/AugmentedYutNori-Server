package com.teamyutnori.yutnori.auth.controller;

import com.teamyutnori.yutnori.auth.FirebaseTokenVerifier;
import com.teamyutnori.yutnori.auth.TokenProvider;
import com.teamyutnori.yutnori.auth.dto.FirebaseLoginRequest;
import com.teamyutnori.yutnori.auth.dto.LoginResponse;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final PlayerService playerService;
    private final TokenProvider tokenProvider;

    @PostMapping("/firebase")
    public LoginResponse firebaseLogin(
            @Valid @RequestBody FirebaseLoginRequest request
    ) {
        // 1. Firebase ID Token 검증
        FirebaseTokenVerifier.VerifiedUser user =
                firebaseTokenVerifier.verify(request.idToken());

        // 2. Firebase UID에 연결된 기존 Player 조회
        //    처음 로그인한 사용자라면 새 Player 생성
        Player player = playerService.findOrCreateByFirebase(
                user.uid(),
                user.email()
        );

        // 3. 우리 서버에서 사용할 토큰 발급
        String token = tokenProvider.createToken(
                player.getPlayerId()
        );

        // 4. 로그인 결과 반환
        return new LoginResponse(
                player.getPlayerId(),
                player.getNickname(),
                token
        );
    }
}