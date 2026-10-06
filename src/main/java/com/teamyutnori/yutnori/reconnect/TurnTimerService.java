package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.config.GameProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

@Service
public class TurnTimerService {


    // 실행 중인 타이머.
    // id는 타이머가 아직 최신인지 확인용
    private record TurnTimer(long id, int team, TimerType type, Instant deadline, ScheduledFuture<?> future){}

    private final GameProperties gameProperties;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;

    private final Map<String, TurnTimer> timers = new HashMap<>();
    private long nextId = 1;

    public TurnTimerService(GameProperties gameProperties,
                            @Qualifier("turnTimerScheduler") TaskScheduler scheduler,
                            Clock clock, ApplicationEventPublisher publisher){
        this.gameProperties = gameProperties;
        this.scheduler = scheduler;
        this.clock = clock;
        this.publisher = publisher;
    }

    // 턴 타이머 시작(turn-time-limit)
    public Instant start(String roomCode, int team){
        return start(roomCode, team, TimerType.TURN, gameProperties.turnTimeLimit());
    }

    // 증강 선택 타이머 시작(augment-select-time-limit)
    public Instant startAugmentSelect(String roomCode, int team){
        return start(roomCode, team, TimerType.AUGMENT_SELECT, gameProperties.augmentSelectTimeLimit());
    }
    // 원하는 시간으로 시작. 마감 시각 반환
    public synchronized Instant start(String roomCode, int team, TimerType type, Duration limit){
        Objects.requireNonNullElse(roomCode, "roomCode");
        Objects.requireNonNull(type, "type");
        if(limit == null || limit.isNegative() || limit.isZero()){
            throw new IllegalArgumentException("제한시간은 0보다 커야합니다. (현재 " + limit + ")");
        }

        cancelInternal(roomCode);

        long id = nextId++;
        Instant deadline = clock.instant().plus(limit);
        ScheduledFuture<?> future = scheduler.schedule(() -> onTimeout(roomCode, id), deadline);
        timers.put(roomCode, new TurnTimer(id, team, type, deadline, future));
        return deadline;
    }

    // 플레이어가 행동했거나 게임이 끝났을 때 호출
    public synchronized void cancel(String roomCode){
        cancelInternal(roomCode);
    }

    //메시지에 넣을 제한 시간(초)
    public int limitSec(TimerType type){
        return (int) limitOf(type).toSeconds();
    }

    // 남은 시간
    public synchronized Optional<Duration> remaining(String roomCode){
        TurnTimer timer = timers.get(roomCode);
        if(timer == null) return Optional.empty();

        Duration left = Duration.between(clock.instant(), timer.deadline());
        return Optional.of(left.isNegative() ? Duration.ZERO : left);
    }

    // 마감 시각
    public synchronized Optional<Instant> deadline(String roomCode){
        TurnTimer timer = timers.get(roomCode);
        return timer == null ? Optional.empty() : Optional.of(timer.deadline());
    }

    // 타이머가 돌고 있는 팀
    public synchronized Optional<Integer> currentTeam(String roomCode){
        TurnTimer timer = timers.get(roomCode);
        return timer == null ? Optional.empty() : Optional.of(timer.team());
    }

    // 돌고 있는 타이머 종류
    public synchronized  Optional<TimerType> currentType(String roomCode){
        TurnTimer timer = timers.get(roomCode);
        return timer == null ? Optional.empty() : Optional.of(timer.type());
    }

    private Duration limitOf(TimerType type){
        return switch(type){
            case TURN -> gameProperties.turnTimeLimit();
            case AUGMENT_SELECT -> gameProperties.augmentSelectTimeLimit();
        };
    }

    private void cancelInternal(String roomCode){
        TurnTimer previous = timers.remove(roomCode);
        if(previous != null) previous.future().cancel(false);
    }

    // 스케줄러 스레드에서 호출
    private void onTimeout(String roomCode, long id){
        TurnTimer expired;
        synchronized (this){
            TurnTimer current = timers.get(roomCode);
            if(current == null || current.id() != id) return;
            timers.remove(roomCode);
            expired = current;
        }

        publisher.publishEvent(new TurnTimeoutEvent(roomCode, expired.team(), expired.type(), expired.deadline()));
    }
}
