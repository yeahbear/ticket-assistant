package org.gecedu.ticketassistant.session;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    @Test
    void listReplacesCorruptedTitleForDisplay() {
        ChatSessionMapper mapper = mock(ChatSessionMapper.class);
        ChatSession broken = new ChatSession();
        broken.setId(7L);
        broken.setTitle("??????");
        broken.setDeleted(0);
        broken.setCreateTime(LocalDateTime.now());
        broken.setUpdateTime(LocalDateTime.now());
        when(mapper.selectList(any())).thenReturn(List.of(broken));

        SessionService service = new SessionService(mapper);

        assertThat(service.list().get(0).getTitle()).isEqualTo("会话 7");
    }
}
