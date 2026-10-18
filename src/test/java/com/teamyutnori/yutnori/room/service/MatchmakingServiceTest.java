package com.teamyutnori.yutnori.room.service;

import com.teamyutnori.yutnori.common.ConflictException;
import com.teamyutnori.yutnori.room.dto.CreateRoomRequest;
import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 빠른 참가 자리 예약: 방 코드를 받은 사람은 JOIN_ROOM에서 ROOM_FULL을 받지 않아야 한다
// (예약 전에는 동시에 몰린 사람들이 같은 방 코드를 받아 늦게 온 사람이 ROOM_FULL — k6 부하 테스트에서 확인)
class MatchmakingServiceTest {

    private static final CreateRoomRequest QUICK4 = new CreateRoomRequest(true, 4, 30);
    private static final CreateRoomRequest QUICK2 = new CreateRoomRequest(true, 2, 30);

    private MutableClock clock;
    private RoomRepository roomRepository;
    private RoomService roomService;
    private MatchmakingService matchmaking;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-18T00:00:00Z"));
        roomRepository = new RoomRepository();
        roomService = new RoomService(roomRepository, null, clock);   // 방 생성·참가에는 GameService를 쓰지 않는다
        matchmaking = new MatchmakingService(roomRepository, roomService, clock);
    }

    @Test
    @DisplayName("빠른 참가 4번은 같은 방, 5번째는 새 방 (참가 전이라도 예약이 자리를 차지)")
    void reservationsFillRoomBeforeJoin() {
        String first = matchmaking.quickJoin(QUICK4, "p1").code();
        assertEquals(first, matchmaking.quickJoin(QUICK4, "p2").code());
        assertEquals(first, matchmaking.quickJoin(QUICK4, null).code());
        assertEquals(first, matchmaking.quickJoin(QUICK4, "p4").code());
        assertNotEquals(first, matchmaking.quickJoin(QUICK4, "p5").code());
    }

    @Test
    @DisplayName("같은 플레이어가 다시 눌러도 자리를 하나만 차지한다")
    void samePlayerKeepsOneSeat() {
        String code = matchmaking.quickJoin(QUICK2, "p1").code();
        assertEquals(code, matchmaking.quickJoin(QUICK2, "p1").code());
        assertEquals(code, matchmaking.quickJoin(QUICK2, "p2").code());   // 남은 1자리
        assertNotEquals(code, matchmaking.quickJoin(QUICK2, "p3").code());
    }

    @Test
    @DisplayName("예약된 자리는 방 코드로 직접 들어온 사람이 가져가지 못하고, 예약한 사람은 들어간다")
    void reservedSeatsBlockOthers() {
        String code = matchmaking.quickJoin(QUICK2, "a").code();
        matchmaking.quickJoin(QUICK2, "b");

        assertThrows(ConflictException.class, () -> roomService.joinRoom(code, "c", "c", null));
        roomService.joinRoom(code, "a", "a", null);
        roomService.joinRoom(code, "b", "b", null);
        assertEquals(2, roomService.getRoomOrThrow(code).getCurrentPlayers());
    }

    @Test
    @DisplayName("JOIN_ROOM이 오지 않은 예약은 만료되면 자리가 다시 풀린다")
    void expiredReservationFreesSeat() {
        String code = matchmaking.quickJoin(QUICK2, "a").code();
        roomService.joinRoom(code, "a", "a", null);
        matchmaking.quickJoin(QUICK2, "b");                       // 마지막 자리 예약, b는 안 들어옴
        assertThrows(ConflictException.class, () -> roomService.joinRoom(code, "c", "c", null));

        clock.advance(MatchmakingService.RESERVATION_TTL.plusSeconds(1));
        assertEquals(code, matchmaking.quickJoin(QUICK2, "c").code());
        roomService.joinRoom(code, "c", "c", null);
    }

    @Test
    @DisplayName("마지막 사람이 나가도 들어오는 중인 예약이 있으면 방을 지우지 않는다")
    void roomKeptWhileReservationPending() {
        String code = matchmaking.quickJoin(QUICK4, "a").code();
        roomService.joinRoom(code, "a", "a", null);
        matchmaking.quickJoin(QUICK4, "b");                       // b는 자리만 잡고 아직 안 들어옴
        roomService.leaveRoom(code, "a");                         // 마지막 참가자 퇴장

        roomService.joinRoom(code, "b", "b", null);               // 방이 남아 있어 들어갈 수 있다
        assertEquals(1, roomService.getRoomOrThrow(code).getCurrentPlayers());
    }

    @Test
    @DisplayName("토큰 없이 잡은 익명 예약은 참가하는 사람이 대신 쓴다 (예전 클라이언트 호환)")
    void anonymousReservationUsedOnJoin() {
        String code = matchmaking.quickJoin(QUICK2, null).code();
        matchmaking.quickJoin(QUICK2, null);
        roomService.joinRoom(code, "x", "x", null);
        roomService.joinRoom(code, "y", "y", null);
        assertEquals(2, roomService.getRoomOrThrow(code).getCurrentPlayers());
    }

    @Test
    @DisplayName("2000명이 동시에 빠른 참가 → 참가해도 ROOM_FULL이 나지 않는다")
    void concurrentQuickJoinNeverFull() throws Exception {
        int players = 2000;
        ExecutorService pool = Executors.newFixedThreadPool(64);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger full = new AtomicInteger();
        Map<String, AtomicInteger> perRoom = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < players; i++) {
            String playerId = "p" + i;
            futures.add(pool.submit(() -> {
                start.await();
                String code = matchmaking.quickJoin(QUICK4, playerId).code();
                try {
                    roomService.joinRoom(code, playerId, playerId, null);
                    perRoom.computeIfAbsent(code, c -> new AtomicInteger()).incrementAndGet();
                } catch (ConflictException e) {
                    full.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(0, full.get());
        assertEquals(players, perRoom.values().stream().mapToInt(AtomicInteger::get).sum());
        for (Map.Entry<String, AtomicInteger> e : perRoom.entrySet()) {
            Room room = roomService.getRoomOrThrow(e.getKey());
            assertEquals(e.getValue().get(), room.getCurrentPlayers());
            assertTrue(room.getCurrentPlayers() <= 4);
        }
    }

    // 시간을 앞으로 돌릴 수 있는 테스트용 Clock
    static final class MutableClock extends Clock {
        private volatile Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
