package com.teamyutnori.yutnori.game.service;

import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.model.GameSetup;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.yut.YutThrowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameSessionRepository sessionRepository;
    private final YutThrowService yutThrowService;
    private final TurnManager turnManager;

    // 2번이 GAME_START 후 호출: 규칙서(GameSetup)를 받아 점수판(GameSession)을 만든다
    public void startGame(String roomCode, GameSetup setup) {
        GameSession session = new GameSession(roomCode, setup);
        sessionRepository.save(session);
        // → 증강 후보 뽑아서 AUGMENT_CHOICES 전송
    }

    // THROW_REQUEST 처리: 저장소에서 꺼내서 → 판단 → 수정 → 방송
    public void handleThrow(String roomCode, int team) {
        GameSession session = sessionRepository.find(roomCode)
                .orElseThrow(() -> new GameException("GAME_NOT_FOUND"));
        synchronized (session) {
            // 검증, 던지기, 상태 변경, THROW_RESULT 방송
        }
    }
}