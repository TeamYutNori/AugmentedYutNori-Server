package com.teamyutnori.yutnori.game.yut;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

// 실제 게임용 난수 (Unity SystemRandomProvider)
@Component
public class SecureRandomProvider implements RandomProvider {
    private final SecureRandom random = new SecureRandom();

    @Override
    public double nextDouble() {
        return random.nextDouble();
    }
}