package com.teamyutnori.yutnori.auth;

import com.teamyutnori.yutnori.common.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenProvider {

    // 서버에서 발급한 토큰과 playerId를 연결해서 저장
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    // 로그인 성공 후 사용할 서버 전용 토큰 발급
    public String createToken(String playerId) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, playerId);
        return token;
    }

    // 전달받은 토큰이 정상적인 토큰인지 확인하고
    // 해당 토큰의 playerId를 반환
    public String getPlayerId(String token) {
        String playerId = tokens.get(token);

        if (playerId == null) {
            throw new UnauthorizedException(
                    "INVALID_TOKEN",
                    "유효하지 않은 인증 토큰입니다."
            );
        }
        return playerId;
    }

    // 필요할 경우 해당 토큰을 더 이상 사용할 수 없도록 제거
    public void removeToken(String token) {
        tokens.remove(token);
    }
}