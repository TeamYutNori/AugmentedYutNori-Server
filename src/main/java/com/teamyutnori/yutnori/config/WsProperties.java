package com.teamyutnori.yutnori.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "yutnori.ws")
public record WsProperties(
        String path, //클라이언트가 접속할 주소
        List<String> allowedOrigins, //Origin정보 읽고 허용할지 체크
        Duration pingInterval //연결 확인할 텀
) {}