package com.teamyutnori.yutnori.game.service;

import java.util.Map;

public record StateDesyncEvent(String roomCode, long seq, Map<String, String> hashes) {
}
