package com.teamyutnori.yutnori.config;

import com.teamyutnori.yutnori.ws.AuthHandshakeInterceptor;
import com.teamyutnori.yutnori.ws.GameWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// WebSocket 접속 주소와 처리 담당을 Spring에 등록하는 설정.
// ws://{host}/ws/rooms/{roomCode} 로 들어온 연결을 GameWebSocketHandler가 처리한다.
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler webSocketHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;
    private final WsProperties properties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, properties.path())
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new));
    }
}
