package org.gecedu.ticketassistant.chat;

import org.gecedu.ticketassistant.ai.AssistantService;
import org.gecedu.ticketassistant.session.SessionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    @Test
    void historyRequiresExistingSession() {
        ChatMemoryService chatMemoryService = mock(ChatMemoryService.class);
        AssistantService assistantService = mock(AssistantService.class);
        SessionService sessionService = mock(SessionService.class);
        ChatController controller = new ChatController(chatMemoryService, assistantService, sessionService);

        when(sessionService.requireSession(99L)).thenThrow(new IllegalArgumentException("会话不存在"));

        assertThatThrownBy(() -> controller.history(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("会话不存在");
        verify(sessionService).requireSession(99L);
    }

    @Test
    void historyReturnsMessagesForExistingSession() {
        ChatMemoryService chatMemoryService = mock(ChatMemoryService.class);
        AssistantService assistantService = mock(AssistantService.class);
        SessionService sessionService = mock(SessionService.class);
        ChatController controller = new ChatController(chatMemoryService, assistantService, sessionService);

        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent("我要购票");
        when(chatMemoryService.history(1L)).thenReturn(List.of(message));

        controller.history(1L);

        verify(sessionService).requireSession(1L);
        verify(chatMemoryService).history(1L);
    }
}
