package com.teamyutnori.yutnori.room.repository;

import com.teamyutnori.yutnori.room.model.Room;
import com.teamyutnori.yutnori.room.model.RoomMember;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomRepositoryContainsPlayerTest {

    @Test
    void 방에_들어가_있는_플레이어만_찾는다() {
        RoomRepository repository = new RoomRepository();
        Room room = new Room("ABC123", true, 4, 30);
        room.addMember(new RoomMember("p1", "윷돌이", null));
        repository.save(room);

        assertThat(repository.containsPlayer("p1")).isTrue();
        assertThat(repository.containsPlayer("p2")).isFalse();
    }
}
