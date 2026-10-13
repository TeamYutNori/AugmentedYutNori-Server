package com.teamyutnori.yutnori.icon;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 플레이어가 가진 프로필 아이콘 한 종류. 같은 아이콘을 또 뽑으면 줄을 늘리지 않고 count만 올린다
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "player_icons",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "icon_id"})
)
public class PlayerIcon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Player.playerId (게임 내부 플레이어 ID)
    @Column(name = "player_id", nullable = false, length = 36)
    private String playerId;

    // profile-icons.json의 id
    @Column(name = "icon_id", nullable = false, length = 64)
    private String iconId;

    // 뽑은 횟수 (1이면 처음 뽑은 것)
    @Column(nullable = false)
    private int count;

    public static PlayerIcon first(String playerId, String iconId) {
        PlayerIcon icon = new PlayerIcon();
        icon.playerId = playerId;
        icon.iconId = iconId;
        icon.count = 1;
        return icon;
    }

    // 중복으로 또 뽑았을 때
    public void addDuplicate() {
        this.count = Math.addExact(this.count, 1);
    }
}
