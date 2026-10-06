package com.teamyutnori.yutnori.reconnect;

import com.teamyutnori.yutnori.config.GameProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TurnTimerServiceTest {

    private static final String ROOM = "ABCD";
    private static final Duration TURN_LIMIT = Duration.ofMillis(100);
    private static final Duration AUGMENT_LIMIT = Duration.ofMillis(150);

    private ThreadPoolTaskScheduler scheduler;
    private List<TurnTimeoutEvent> events;
    private TurnTimerService service;

    @BeforeEach
    void setUp() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        events = new CopyOnWriteArrayList<>();
        service = createService(TURN_LIMIT, AUGMENT_LIMIT);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    // 턴 제한시간이 지나면 TURN 이벤트가 한 번 발행된다 (방, 팀 정보 포함)
    @Test
    void timeout() throws InterruptedException {
        service.start(ROOM, 1);

        waitFor(1, Duration.ofSeconds(1));

        assertThat(events).hasSize(1);
        TurnTimeoutEvent event = events.get(0);
        assertThat(event.roomCode()).isEqualTo(ROOM);
        assertThat(event.team()).isEqualTo(1);
        assertThat(event.type()).isEqualTo(TimerType.TURN);
        assertThat(service.remaining(ROOM)).isEmpty();
    }

    // 증강 선택 타이머는 AUGMENT_SELECT 이벤트로 발행된다
    @Test
    void augmentSelect() throws InterruptedException {
        service.startAugmentSelect(ROOM, 0);

        waitFor(1, Duration.ofSeconds(1));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(TimerType.AUGMENT_SELECT);
        assertThat(events.get(0).team()).isEqualTo(0);
    }

    // 시간 전에 cancel하면 이벤트가 발행되지 않는다
    @Test
    void cancel() throws InterruptedException {
        service.start(ROOM, 0);
        service.cancel(ROOM);

        Thread.sleep(300);

        assertThat(events).isEmpty();
    }

    // 같은 방에서 다시 start하면 이전 타이머는 취소되고 새 타이머만 만료된다
    @Test
    void restart() throws InterruptedException {
        service.start(ROOM, 0, TimerType.TURN, TURN_LIMIT);
        service.start(ROOM, 1, TimerType.AUGMENT_SELECT, Duration.ofMillis(200));

        Thread.sleep(400);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).team()).isEqualTo(1);
        assertThat(events.get(0).type()).isEqualTo(TimerType.AUGMENT_SELECT);
    }

    // 방마다 타이머가 따로 돈다
    @Test
    void separateRoom() throws InterruptedException {
        service.start("ROOM1", 0);
        service.start("ROOM2", 1);

        waitFor(2, Duration.ofSeconds(1));

        assertThat(events).extracting(TurnTimeoutEvent::roomCode)
                .containsExactlyInAnyOrder("ROOM1", "ROOM2");
    }

    // 남은 시간, 마감 시각, 팀을 조회할 수 있고 취소하면 empty
    @Test
    void remaining() {
        service.start(ROOM, 0, TimerType.TURN, Duration.ofSeconds(5));

        assertThat(service.remaining(ROOM)).get()
                .satisfies(left -> assertThat(left).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(5)));
        assertThat(service.currentTeam(ROOM)).contains(0);
        assertThat(service.deadline(ROOM)).isPresent();

        service.cancel(ROOM);

        assertThat(service.remaining(ROOM)).isEmpty();
        assertThat(service.currentTeam(ROOM)).isEmpty();
    }

    // 지금 돌고 있는 타이머 종류를 조회할 수 있다
    @Test
    void currentType() {
        service.start(ROOM, 0, TimerType.TURN, Duration.ofSeconds(5));
        assertThat(service.currentType(ROOM)).contains(TimerType.TURN);

        service.start(ROOM, 0, TimerType.AUGMENT_SELECT, Duration.ofSeconds(5));
        assertThat(service.currentType(ROOM)).contains(TimerType.AUGMENT_SELECT);

        service.cancel(ROOM);
        assertThat(service.currentType(ROOM)).isEmpty();
    }

    // limitSec는 설정값을 초 단위로 돌려준다 (메시지의 turnTimeLimitSec, timeLimitSec용)
    @Test
    void limitSec() {
        TurnTimerService real = createService(Duration.ofSeconds(30), Duration.ofSeconds(20));

        assertThat(real.limitSec(TimerType.TURN)).isEqualTo(30);
        assertThat(real.limitSec(TimerType.AUGMENT_SELECT)).isEqualTo(20);
    }

    // 0 이하 또는 null 제한시간은 예외
    @Test
    void invalidLimit() {
        assertThatThrownBy(() -> service.start(ROOM, 0, TimerType.TURN, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.start(ROOM, 0, TimerType.TURN, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.start(ROOM, 0, TimerType.TURN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 타이머 종류가 null이면 예외
    @Test
    void nullType() {
        assertThatThrownBy(() -> service.start(ROOM, 0, null, TURN_LIMIT))
                .isInstanceOf(NullPointerException.class);
    }

    // 없는 방을 cancel해도 예외가 나지 않는다
    @Test
    void cancelUnknown() {
        service.cancel("NONE");

        assertThat(service.remaining("NONE")).isEmpty();
    }

    // 턴 / 증강 제한시간을 지정해서 서비스를 만든다 (나머지 설정은 실제 값과 비슷하게)
    private TurnTimerService createService(Duration turnLimit, Duration augmentLimit) {
        GameProperties properties = new GameProperties(
                2, 2, turnLimit, 3, augmentLimit, Duration.ofSeconds(30));
        return new TurnTimerService(properties, scheduler, Clock.systemUTC(),
                event -> events.add((TurnTimeoutEvent) event));
    }

    // 이벤트가 count개 모일 때까지 최대 timeout만큼 기다린다
    private void waitFor(int count, Duration timeout) throws InterruptedException {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (events.size() < count && System.currentTimeMillis() < end) {
            Thread.sleep(10);
        }
    }
}