package com.teamyutnori.yutnori.icon;

// profile-icons.json의 아이콘 한 개 정의. 필드 이름은 json 키와 같아야 자동으로 채워진다
//  - id     : 아이콘 식별자. Unity Resources/ProfileIcons/{id}.png와 같아야 한다
//  - name   : 화면 표시 이름 (서버는 사용하지 않음)
//  - weight : 뽑기 가중치. 클수록 잘 나온다. 비우면 1 (모두 같은 확률)
public record IconDefinition(String id, String name, Integer weight) {

    public int weightOrDefault() {
        return weight == null ? 1 : weight;
    }
}
