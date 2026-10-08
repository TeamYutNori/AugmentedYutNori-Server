package com.teamyutnori.yutnori.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

// 서비스 계정 키로 Firebase를 초기화하고 ID 토큰 검증용 FirebaseAuth 등록
@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    private final FirebaseProperties properties;

    @Bean
    public FirebaseApp firebaseApp() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        try (FileInputStream fis = new FileInputStream(properties.credentialsPath())){
            GoogleCredentials googleCredentials = GoogleCredentials.fromStream(fis);
            FirebaseOptions build = FirebaseOptions.builder().setCredentials(googleCredentials).build();
            return FirebaseApp.initializeApp(build);
        } catch (IOException e) {
            throw new IllegalStateException("Firebase 서비스 계정 키를 읽을 수 없음: " + properties.credentialsPath(), e);
        }
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp app) {
        return FirebaseAuth.getInstance(app);
    }
}
