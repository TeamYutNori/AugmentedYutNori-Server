package com.teamyutnori.yutnori.gacha;

// 뽑기 요청 1번이 성공하면 GachaService가 1개 발행 (업적: 뽑기 횟수). draws는 뽑은 횟수 (1회면 1, 10연차면 10)
// 뽑기 트랜잭션이 커밋된 뒤에 집계된다 (골드 부족 등으로 실패한 뽑기는 세지 않음)
public record GachaDrawnEvent(String playerId, int draws) {}
