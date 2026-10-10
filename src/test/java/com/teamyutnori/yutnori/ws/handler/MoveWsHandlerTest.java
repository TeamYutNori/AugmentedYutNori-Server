package com.teamyutnori.yutnori.ws.handler;

import com.teamyutnori.yutnori.game.dto.GameRequests.MoveRequest;
import com.teamyutnori.yutnori.game.service.GameService;
import com.teamyutnori.yutnori.ws.WsMessageContext;
import com.teamyutnori.yutnori.ws.dto.MessageType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MoveWsHandlerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final GameService gameService = mock(GameService.class);
    private final MoveWsHandler handler = new MoveWsHandler(gameService, objectMapper);
    private final WsMessageContext ctx = new WsMessageContext(null, "ROOM", "p0", null);

    // MOVE 타입을 담당한다
    @Test
    void type() {
        assertThat(handler.type()).isEqualTo(MessageType.MOVE);
    }

    // 클라이언트 MoveMessage JSON을 읽어 GameService에 넘긴다
    // 클라가 아직 보내는 결과값(capturedCount, isFinished 등)은 무시된다 → 서버가 판으로 계산
    @Test
    void parse() {
        String json = """
                {"pieceId":101,"moveCount":3,"destinationNodeId":12,"capturedCount":1,
                 "isFinished":true,"hasExtraThrow":false,"hasRemainingAction":true,
                 "stateHash":"0123456789abcdef"}
                """;

        handler.handle(ctx, objectMapper.readTree(json));

        ArgumentCaptor<MoveRequest> captor = ArgumentCaptor.forClass(MoveRequest.class);
        verify(gameService).move(eq("ROOM"), eq("p0"), captor.capture());
        MoveRequest request = captor.getValue();
        assertThat(request.pieceId()).isEqualTo(101);
        assertThat(request.moveCount()).isEqualTo(3);
        assertThat(request.destinationNodeId()).isEqualTo(12);
        assertThat(request.stateHash()).isEqualTo("0123456789abcdef");
    }
}
