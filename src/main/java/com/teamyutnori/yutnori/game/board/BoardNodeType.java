package com.teamyutnori.yutnori.game.board;

import com.fasterxml.jackson.annotation.JsonCreator;

// 칸 종류 (Unity BoardNodeType과 같은 순서·의미)
public enum BoardNodeType {
    NORMAL,
    START,   // 판 밖 출발 칸. 판에 안 나온 말이 있는 곳이자, END를 지나 여기로 들어오면 골인
    END,     // 참먹이. 여기를 지나가야 골인
    CORNER,  // 모, 뒷모, 찌모
    CENTER;  // 방

    // 판 json에서 읽을 때 "START", "Start", 1(Unity enum 숫자) 모두 받는다
    // Unity에서 내보낸 json이 숫자든 문자열이든 같은 칸 종류가 되도록
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static BoardNodeType from(Object value) {
        if (value instanceof Number n) {
            int index = n.intValue();
            BoardNodeType[] all = values();
            if (index < 0 || index >= all.length) throw new IllegalArgumentException("없는 칸 종류 번호: " + index);
            return all[index];
        }
        return valueOf(String.valueOf(value).trim().toUpperCase());
    }
}
