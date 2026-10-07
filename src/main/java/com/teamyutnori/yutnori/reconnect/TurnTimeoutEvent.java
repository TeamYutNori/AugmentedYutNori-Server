package com.teamyutnori.yutnori.reconnect;

import java.time.Instant;

public record TurnTimeoutEvent(String roomCode, int team, TimerType type, Instant deadline){
}
