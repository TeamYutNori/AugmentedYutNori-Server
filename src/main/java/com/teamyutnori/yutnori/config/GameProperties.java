package com.teamyutnori.yutnori.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "yutnori.game")
public record GameProperties(
        int maxPlayers,
        int piecesPerTeam,
        Duration turnTimeLimit,
        int augmentChoiceCount, //선택할 증강 개수
        Duration augmentSelectTimeLimit, // 증강 선택 시간 제한
        Duration reconnectGrace // 재접속 유예 기간
) {}