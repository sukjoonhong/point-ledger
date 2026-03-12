package io.github.sukjoonhong.pointledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukjoonhong.pointledger.domain.dto.PointCommand;
import io.github.sukjoonhong.pointledger.infrastructure.messaging.local.LocalPointQueueManager;
import io.github.sukjoonhong.pointledger.web.v1.PointController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PointController.class)
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LocalPointQueueManager queueManager;

    @Test
    @DisplayName("이벤트 인큐 요청 시 큐에 데이터를 넣고 202 응답을 내려야 한다")
    void enqueueEventReturnsAccepted() throws Exception {
        // given
        PointCommand command = PointCommand.builder()
                .pointKey("api-evt-1")
                .memberId(1L)
                .amount(500L)
                .build();

        doNothing().when(queueManager).offer(any(PointCommand.class));

        // when & then
        mockMvc.perform(post("/v1/points/enqueue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isAccepted());

        verify(queueManager).offer(any(PointCommand.class));
    }
}