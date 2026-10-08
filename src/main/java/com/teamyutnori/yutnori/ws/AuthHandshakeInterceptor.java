package com.teamyutnori.yutnori.ws;

import com.teamyutnori.yutnori.auth.TokenProvider;
import com.teamyutnori.yutnori.common.UnauthorizedException;
import com.teamyutnori.yutnori.player.entity.Player;
import com.teamyutnori.yutnori.player.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final TokenProvider tokenProvider;
    private final PlayerRepository playerRepository;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        // WebSocket 주소의 ?guestToken= 값 가져오기
        String token = getGuestToken(request.getURI());

        // 토큰이 없으면 연결 거부
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // 토큰으로 Player 찾기
        Player player = findPlayer(token);

        // 유효한 Player가 아니면 연결 거부
        if (player == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // WebSocket 주소에서 roomCode 가져오기
        String roomCode = getRoomCode(request.getURI());

        // WsMessageContext에서 사용할 값을 WebSocket 세션에 저장
        attributes.put(
                WsMessageContext.ATTR_PLAYER_ID,
                player.getPlayerId()
        );

        attributes.put(
                WsMessageContext.ATTR_NICKNAME,
                player.getNickname()
        );

        attributes.put(
                WsMessageContext.ATTR_ROOM_CODE,
                roomCode
        );

        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }
    // Firebase 로그인 토큰 또는 기존 게스트 토큰으로 Player 조회
    private Player findPlayer(String token) {
        try {
            // Firebase 로그인 후 서버가 발급한 토큰 확인
            String playerId = tokenProvider.getPlayerId(token);
            return playerRepository.findByPlayerId(playerId)
                    .orElse(null);
        } catch (UnauthorizedException e) {
            // 서버 토큰이 아니라면 기존 게스트 토큰인지 확인
            return playerRepository.findByGuestToken(token)
                    .orElse(null);
        }
    }

    // WebSocket 주소에서 guestToken 값 가져오기
    private String getGuestToken(URI uri) {
        String query = uri.getQuery();
        if (query == null) {
            return null;
        }

        for (String parameter : query.split("&")) {
            String[] parts = parameter.split("=", 2);
            if (parts.length == 2
                    && parts[0].equals("guestToken")) {

                return parts[1];
            }
        }
        return null;
    }

    // WebSocket 주소에서 roomCode 가져오기
    // 예: /ws/rooms/ABC123 → ABC123
    private String getRoomCode(URI uri) {
        String path = uri.getPath();
        return path.substring(
                path.lastIndexOf('/') + 1
        );
    }
}