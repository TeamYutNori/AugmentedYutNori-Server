package com.teamyutnori.yutnori.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Firebase Admin SDK 초기화에 쓸 설정값
@ConfigurationProperties(prefix = "yutnori.firebase")
public record FirebaseProperties(
        String credentialsPath //서비스 계정 키(json) 파일 경로
) {}