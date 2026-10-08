package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.game.model.GameSession;
import com.teamyutnori.yutnori.game.repository.GameSessionRepository;
import com.teamyutnori.yutnori.game.service.GameService;
import com.teamyutnori.yutnori.game.service.StateDesyncEvent;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.SequenceTracker;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.GameSnapshotMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconnectService {
    
    private final GameSessionRepository sessionRepository;
    private final RoomBroadcaster broadcaster;
    private final TurnTimerService turnTimerService;
    private final SequenceTracker sequenceTracker;
    
    @EventListener
    public void onReconnected(PlayerReconnectedEvent event){
        sendSnapshot(event.roomCode(), event.playerId());
    }
    
    @EventListener
    public void onDesync(StateDesyncEvent event){
        sessionRepository.find(event.roomCode()).ifPresent(session -> {
            log.warn("DESYNC 재동기화 room={} seq={}", event.roomCode(), event.seq());
            for(String playerId : session.getPlayerTeams().keySet()){
                sendSnapshot(event.roomCode(), playerId);
            }
        });
    }
    
    public boolean sendSnapshot(String roomCode, String playerId){
        Optional<GameSession> found = sessionRepository.find(roomCode);
        if(found.isEmpty()) return false;
        GameSession session = found.get();

        GameSnapshotMessage snapshot;
        synchronized (session){
            Optional<Integer> myTeam = session.teamOf(playerId);
            if(myTeam.isEmpty()) return false;
            snapshot = buildSnapshot(roomCode, session, myTeam.get());
        }
        
        broadcaster.sendTo(roomCode, playerId, MessageType.GAME_SNAPSHOT, snapshot);
        return true;
    }

    private GameSnapshotMessage buildSnapshot(String roomCode, GameSession session, Integer myTeam) {
        Map<Integer, List<String>> owned = new TreeMap<>();
        session.getOwnedAugments().forEach((team, ids) -> owned.put(team, ids.stream().sorted().toList()));

        List<String> offered = session.getOfferedAugments().getOrDefault(myTeam, List.of());

        TimerType timerType = turnTimerService.currentType(roomCode).orElse(null);
        int remainingSec = turnTimerService.remaining(roomCode).map(ReconnectService::ceilSeconds).orElse(0);

        return new GameSnapshotMessage(
                sequenceTracker.current(roomCode),
                session.getPhase(),
                myTeam,
                session.getCurrentTeam(),
                session.getRemainingThrows(),
                session.getTurnNumber(),
                List.copyOf(session.getStoredResults()),
                owned,
                List.copyOf(offered),
                session.getWinnerTeam(),
                timerType,
                remainingSec
        );
    }

    private static int ceilSeconds(Duration duration) {
        long millis = duration.toMillis();
        return (int) ((millis + 999) / 1000);
    }


}
