package com.teamyutnori.yutnori.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// CORS 허용 출처 설정값
@ConfigurationProperties(prefix = "yutnori.cors")
public record CorsProperties(
        List<String> allowedOrigins //HTTP API를 호출할 수 있는 출처 (와일드카드 패턴 가능)
) {}