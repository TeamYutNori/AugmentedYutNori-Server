package com.teamyutnori.yutnori.gacha;

// 뽑기 1회 성공 시 GachaService가 발행 (업적: 뽑기 횟수)
// 뽑기 트랜잭션이 커밋된 뒤에 집계된다 (골드 부족 등으로 실패한 뽑기는 세지 않음)
public record GachaDrawnEvent(String playerId, String iconId) {}
