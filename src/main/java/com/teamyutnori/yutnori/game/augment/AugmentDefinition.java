package com.teamyutnori.yutnori.game.augment;

// augments.json의 증강 한 개 정의. 필드 이름은 json 키와 같아야 자동으로 채워진다 (Unity AugmentDefinition과 동일)
//  - id          : 증강 식별자. 클라·서버가 주고받는 값 (예: "Rethrow")
//  - name        : 화면 표시 이름 (서버는 사용하지 않음)
//  - description : 화면 표시 설명 (서버는 사용하지 않음)
//  - consumable  : true면 한 번 쓰면 사라지는 1회용 증강 (예: Rethrow, Shield)
public record AugmentDefinition(String id, String name, String description, boolean consumable) {}
