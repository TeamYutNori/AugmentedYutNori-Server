package com.teamyutnori.yutnori.game.yut;

// 윷 한 번 던진 결과. sticks: 길이 4, true = 평평한 면, [0]이 표시된 윷 (Unity YutThrowOutcome과 동일)
public record YutThrowOutcome(boolean[] sticks, YutResult result) {}
