package com.teamyutnori.yutnori.game.yut;

public enum YutResult {
    BackDo(-1), Do(1), Gae(2), Geol(3), Yut(4), Mo(5);

    private final int steps;
    YutResult(int steps) { this.steps = steps; }

    public int steps() { return steps; }
    public boolean grantsExtraThrow() { return this == Yut || this == Mo; }
}