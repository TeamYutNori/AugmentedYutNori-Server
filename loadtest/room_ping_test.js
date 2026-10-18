// 직업윷놀이 서버 부하 테스트 (k6)
// 시나리오: 가상 유저 1명 = 플레이어 1명
//   1) POST /players            에디터용 게스트 플레이어 생성 → guestToken
//   2) POST /rooms/quick-join   빠른 참가로 방 코드 받기 (방이 차면 새 방이 생김)
//   3) WebSocket 접속 → JOIN_ROOM → 1초마다 PING, PONG까지 걸린 시간(왕복 지연) 측정
//   4) 일정 시간 뒤 LEAVE_ROOM 후 종료
//
// 실행 예:
//   k6 run room_ping_test.js                                  (기본: 로컬 서버)
//   k6 run -e BASE=https://augmented-yutnori.duckdns.org room_ping_test.js
//   k6 run -e STAY=60 room_ping_test.js                       (방에 머무는 시간, 초)

import http from 'k6/http';
import ws from 'k6/ws';
import { check } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const WS_BASE = BASE.replace(/^http/, 'ws');
const STAY_SEC = Number(__ENV.STAY || 30);

// 직접 만든 측정값
const pingRtt = new Trend('ws_ping_rtt', true);        // PING → PONG 왕복 시간
const joinTime = new Trend('ws_join_time', true);      // 접속 → 첫 ROOM_STATE 까지
const wsErrors = new Counter('ws_errors');             // 서버가 보낸 ERROR 메시지 수 (전체)
const wsRoomFull = new Counter('ws_error_room_full');  // 그중 ROOM_FULL (빠른 참가 자리 겹침)
const wsRoomGone = new Counter('ws_error_room_not_found'); // 그중 ROOM_NOT_FOUND (받은 방이 그새 지워짐)
const wsHandshakeFail = new Counter('ws_handshake_failed'); // WebSocket 접속 자체 실패 (서버 대기열 초과 등)
const joinOk = new Rate('ws_join_success');            // 방 참가 성공률

// 부하 단계: 0 → 50명(1분) → 50명 유지(2분) → 200명(1분) → 200명 유지(2분) → 0
export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '2m', target: 50 },
    { duration: '1m', target: 200 },
    { duration: '2m', target: 200 },
    { duration: '30s', target: 0 },
  ],
  // 합격 기준: 이걸 넘으면 k6가 실패로 표시한다
  thresholds: {
    http_req_failed: ['rate<0.01'],          // REST 실패율 1% 미만
    http_req_duration: ['p(95)<500'],        // REST 95%가 0.5초 안
    ws_ping_rtt: ['p(95)<200'],              // 핑 왕복 95%가 0.2초 안
    ws_join_success: ['rate>0.99'],          // 방 참가 99% 이상 성공
  },
};

const json = { headers: { 'Content-Type': 'application/json' } };

export default function () {
  // 1) 게스트 플레이어 생성 (닉네임은 20자 이하, 중복 불가)
  const nickname = `k6_${__VU}_${__ITER}_${Date.now() % 100000}`.slice(0, 20);
  const p = http.post(`${BASE}/players`, JSON.stringify({ nickname }), json);
  if (!check(p, { 'player created': (r) => r.status === 200 })) return;
  const token = p.json('guestToken');
  const myId = p.json('playerId');

  // 2) 빠른 참가로 방 코드 받기 (토큰을 보내면 서버가 내 이름으로 자리를 예약해 둔다)
  const q = http.post(`${BASE}/rooms/quick-join`,
    JSON.stringify({ isPublic: true, maxPlayers: 4, timeLimit: 30 }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } });
  if (!check(q, { 'room joined (REST)': (r) => r.status === 200 })) return;
  const code = q.json('code');

  // 3) WebSocket
  const url = `${WS_BASE}/ws/rooms/${code}?guestToken=${encodeURIComponent(token)}`;
  const opened = Date.now();
  let joined = false;

  const res = ws.connect(url, {}, (socket) => {
    socket.on('open', () => {
      socket.send(JSON.stringify({ type: 'JOIN_ROOM', seq: 0, payload: {} }));

      socket.setInterval(() => {
        socket.send(JSON.stringify({ type: 'PING', seq: 0, payload: { clientTime: Date.now() } }));
      }, 1000);

      socket.setTimeout(() => {
        socket.send(JSON.stringify({ type: 'LEAVE_ROOM', seq: 0, payload: {} }));
        socket.close();
      }, STAY_SEC * 1000);
    });

    socket.on('message', (raw) => {
      const msg = JSON.parse(raw);
      // 같은 방 주소로 붙은 사람은 남의 ROOM_STATE도 받으므로, 참가자 목록에 내가 있을 때만 참가 성공으로 센다
      if (msg.type === 'ROOM_STATE' && !joined &&
          (msg.payload.members || []).some((m) => m.playerId === myId)) {
        joined = true;
        joinTime.add(Date.now() - opened);
      } else if (msg.type === 'PONG') {
        pingRtt.add(Date.now() - msg.payload.clientTime);
      } else if (msg.type === 'ERROR') {
        wsErrors.add(1);
        const code = msg.payload && msg.payload.code;
        if (code === 'ROOM_FULL') wsRoomFull.add(1);
        else if (code === 'ROOM_NOT_FOUND') wsRoomGone.add(1);
      }
    });

    socket.on('error', () => wsErrors.add(1));
  });

  const handshakeOk = check(res, { 'ws handshake 101': (r) => r && r.status === 101 });
  if (!handshakeOk) wsHandshakeFail.add(1);
  joinOk.add(joined);
}
