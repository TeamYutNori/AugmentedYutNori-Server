# AugmentedYutNori-Server

증강 윷놀이(AugmentedYutNori) 멀티플레이 서버입니다.
Unity 클라이언트: [TeamYutNori/AugmentedYutNori](https://github.com/TeamYutNori/AugmentedYutNori)

## 기술 스택

- Java 21, Spring Boot 4.1
- WebSocket(게임 진행), REST(로그인·전적)
- MySQL 8.0, Redis 7.4 (Docker Compose)

## 사전 준비

1. Docker Desktop 설치
2. 프로젝트 루트에 `.env` 파일 생성 (Git에 올리지 않음)

```
DB_USERNAME=root
DB_PASSWORD=원하는비밀번호
```

## 실행 방법

### 개발할 때

DB만 Docker로 띄우고, 서버는 IntelliJ에서 실행합니다.

```bash
docker compose up -d mysql redis
```

그다음 IntelliJ에서 `AugmentedYutNoriServerApplication`을 실행합니다.

### 전체 실행

서버까지 전부 Docker로 실행합니다.

```bash
docker compose up --build
```

### 종료

```bash
docker compose down
```

## 접속 주소

| 용도 | 주소 |
|---|---|
| REST API | `http://localhost:8080` |
| WebSocket | `ws://localhost:8080/ws/rooms/{roomCode}` |

## 설정

게임 규칙과 WebSocket 설정은 `src/main/resources/application.properties`에서 변경합니다.

| 키 | 기본값 | 설명 |
|---|---|---|
| `yutnori.game.max-players` | 2 | 방 최대 인원 |
| `yutnori.game.pieces-per-team` | 2 | 팀당 말 개수 |
| `yutnori.game.turn-time-limit` | 30s | 턴 제한시간 |
| `yutnori.game.augment-choice-count` | 3 | 증강 후보 개수 |
| `yutnori.game.augment-select-time-limit` | 20s | 증강 선택 제한시간 |
| `yutnori.game.reconnect-grace` | 30s | 연결 끊김 후 재접속 유예시간 |
| `yutnori.ws.path` | /ws | WebSocket 기본 경로 |
| `yutnori.ws.allowed-origins` | * | 허용 Origin (배포 시 실제 도메인으로 변경) |
| `yutnori.ws.ping-interval` | 10s | 연결 확인 주기 |
