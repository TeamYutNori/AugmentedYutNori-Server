package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.config.GameProperties;
import com.teamyutnori.yutnori.ws.RoomBroadcaster;
import com.teamyutnori.yutnori.ws.RoomSessionRegistry;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import com.teamyutnori.yutnori.ws.dto.WsMessages.PlayerDisconnectedMessage;
import com.teamyutnori.yutnori.ws.dto.WsMessages.PlayerReconnectedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
public class ConnectionCleanup {

    private record Key(String roomCode, String playerId) {}

    private record Waiting(long id, ScheduledFuture<?> future) {}

    private final RoomBroadcaster broadcaster;
    private final RoomSessionRegistry registry;
    private final GameProperties gameProperties;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;

    private final Map<Key, Waiting> waitings = new HashMap<>();
    private long nextId = 1;

    public ConnectionCleanup(RoomBroadcaster broadcaster, RoomSessionRegistry registry, GameProperties gameProperties,
                             @Qualifier("turnTimerScheduler") TaskScheduler scheduler, Clock clock, ApplicationEventPublisher publisher) {
        this.broadcaster = broadcaster;
        this.registry = registry;
        this.gameProperties = gameProperties;
        this.scheduler = scheduler;
        this.clock = clock;
        this.publisher = publisher;
    }

    public void onDisconnected(WsMessageContext context){
        String roomCode = context.roomCode();
        String playerId = context.playerId();

        if(registry.find(roomCode, playerId).isPresent()) return;

        Key key = new Key(roomCode, playerId);
        synchronized (this){
            cancelInternal(key);
            long id = nextId++;
            ScheduledFuture<?> future = scheduler.schedule(() ->
                    onGraceExpired(key, id), clock.instant().plus(gameProperties.reconnectGrace()));
            waitings.put(key, new Waiting(id, future));
        }

        int graceSec = (int) gameProperties.reconnectGrace().toSeconds();
        broadcaster.broadcast(roomCode, MessageType.PLAYER_DISCONNECTED, new PlayerDisconnectedMessage(playerId, graceSec));
        log.info("재접속 대기 시작 room={}, player={}, grace={}s", roomCode, playerId, graceSec);
    }

    public void onConnected(WsMessageContext context){
        Key key = new Key(context.roomCode(), context.playerId());
        boolean wasWaiting;
        synchronized (this){
            wasWaiting = cancelInternal(key);
        }

        if(wasWaiting){
            broadcaster.broadcast(context.roomCode(), MessageType.PLAYER_RECONNECTED, new PlayerReconnectedMessage(context.playerId()));
            log.info("재접속 room={}, player={}", context.roomCode(), context.playerId());
        }
    }

    public synchronized void cancel(String roomCode, String playerId){
        cancelInternal(new Key(roomCode, playerId));
    }

    public synchronized void clearRoom(String roomCode){
        waitings.keySet().removeIf(key ->{
            if(!key.roomCode().equals(roomCode)) return false;
            waitings.get(key).future().cancel(false);
            return true;
        });
    }

    public synchronized boolean isWaiting(String roomCode, String playerId){
        return waitings.containsKey(new Key(roomCode, playerId));
    }

    public boolean cancelInternal(Key key){
        Waiting previous = waitings.remove(key);
        if(previous == null) return false;
        previous.future().cancel(false);
        return true;
    }

    private void onGraceExpired(Key key, long id){
        synchronized (this){
            Waiting current = waitings.get(key);
            if(current == null || current.id() != id) return;
            waitings.remove(key);
        }
        log.info("재접속 대기 시간 초과 room={} player={}", key.roomCode(), key.playerId());
        publisher.publishEvent(new DisconnectTimeoutEvent(key.roomCode(), key.playerId()));
    }
}
