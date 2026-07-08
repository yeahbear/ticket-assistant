package org.gecedu.ticketassistant.ai;

import org.gecedu.ticketassistant.chat.ChatMemoryService;
import org.gecedu.ticketassistant.chat.ChatMessage;
import org.gecedu.ticketassistant.session.ChatSession;
import org.gecedu.ticketassistant.session.SessionService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantServiceTest {

    @Test
    void blankMessageIsRejectedBeforeSavingHistory() {
        SessionService sessionService = mock(SessionService.class);
        ChatMemoryService chatMemoryService = mock(ChatMemoryService.class);
        LocalAssistantService localAssistantService = mock(LocalAssistantService.class);
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        AssistantService assistantService = new AssistantService(
                sessionService,
                chatMemoryService,
                localAssistantService,
                deepSeekClient,
                true
        );

        ChatSession session = new ChatSession();
        session.setId(1L);
        when(sessionService.requireSession(1L)).thenReturn(session);

        assertThatThrownBy(() -> assistantService.stream(1L, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("消息不能为空");

        verify(chatMemoryService, never()).save(eq(1L), anyString(), anyString());
    }

    @Test
    void toolIntentUsesLocalAssistantEvenWhenDeepSeekIsEnabled() {
        SessionService sessionService = mock(SessionService.class);
        ChatMemoryService chatMemoryService = mock(ChatMemoryService.class);
        LocalAssistantService localAssistantService = mock(LocalAssistantService.class);
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        AssistantService assistantService = new AssistantService(
                sessionService,
                chatMemoryService,
                localAssistantService,
                deepSeekClient,
                true
        );

        ChatSession session = new ChatSession();
        session.setId(1L);
        when(sessionService.requireSession(1L)).thenReturn(session);
        when(chatMemoryService.history(1L)).thenReturn(List.of());
        when(deepSeekClient.enabled()).thenReturn(true);
        when(deepSeekClient.stream(anyString(), anyList())).thenReturn(Flux.just("wrong"));
        when(localAssistantService.answer(eq("查询广州天气"), anyList())).thenReturn("广州实时天气：多云");

        StepVerifier.create(assistantService.stream(1L, "查询广州天气").collectList())
                .expectNextMatches(parts -> String.join("", parts).contains("广州实时天气"))
                .verifyComplete();

        verify(localAssistantService).answer(eq("查询广州天气"), anyList());
        verify(deepSeekClient, never()).stream(anyString(), anyList());
    }

    @Test
    void trainTicketSearchUsesLocalAssistantEvenWhenDeepSeekIsEnabled() {
        SessionService sessionService = mock(SessionService.class);
        ChatMemoryService chatMemoryService = mock(ChatMemoryService.class);
        LocalAssistantService localAssistantService = mock(LocalAssistantService.class);
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        AssistantService assistantService = new AssistantService(
                sessionService,
                chatMemoryService,
                localAssistantService,
                deepSeekClient,
                true
        );

        ChatSession session = new ChatSession();
        session.setId(11L);
        when(sessionService.requireSession(11L)).thenReturn(session);
        when(chatMemoryService.history(11L)).thenReturn(List.of());
        when(deepSeekClient.enabled()).thenReturn(true);
        when(localAssistantService.answer(eq("查询广州到上海 2026-07-15 车票"), anyList())).thenReturn("查询到真实余票");

        StepVerifier.create(assistantService.stream(11L, "查询广州到上海 2026-07-15 车票").collectList())
                .expectNextMatches(parts -> String.join("", parts).contains("真实余票"))
                .verifyComplete();

        verify(localAssistantService).answer(eq("查询广州到上海 2026-07-15 车票"), anyList());
        verify(deepSeekClient, never()).stream(anyString(), anyList());
    }

    @Test
    void incompleteBookingContinuationUsesLocalAssistantEvenWhenDeepSeekIsEnabled() {
        SessionService sessionService = mock(SessionService.class);
        ChatMemoryService chatMemoryService = mock(ChatMemoryService.class);
        LocalAssistantService localAssistantService = mock(LocalAssistantService.class);
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        AssistantService assistantService = new AssistantService(
                sessionService,
                chatMemoryService,
                localAssistantService,
                deepSeekClient,
                true
        );

        ChatSession session = new ChatSession();
        session.setId(2L);
        ChatMessage assistantPrompt = new ChatMessage();
        assistantPrompt.setRole("assistant");
        assistantPrompt.setContent("可以为您办理购票。\n\n订票需要提供以下信息：");
        when(sessionService.requireSession(2L)).thenReturn(session);
        when(chatMemoryService.history(2L)).thenReturn(List.of(assistantPrompt));
        when(deepSeekClient.enabled()).thenReturn(true);
        when(localAssistantService.answer(eq("乘车人姓名张三"), anyList())).thenReturn("当前还缺：身份证号、车次、乘车日期、座位类型");

        StepVerifier.create(assistantService.stream(2L, "乘车人姓名张三").collectList())
                .expectNextMatches(parts -> String.join("", parts).contains("当前还缺"))
                .verifyComplete();

        verify(localAssistantService).answer(eq("乘车人姓名张三"), anyList());
        verify(deepSeekClient, never()).stream(anyString(), anyList());
    }

    @Test
    void ordinaryQuestionDuringPendingBookingUsesDeepSeekWhenEnabled() {
        SessionService sessionService = mock(SessionService.class);
        ChatMemoryService chatMemoryService = mock(ChatMemoryService.class);
        LocalAssistantService localAssistantService = mock(LocalAssistantService.class);
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        AssistantService assistantService = new AssistantService(
                sessionService,
                chatMemoryService,
                localAssistantService,
                deepSeekClient,
                true
        );

        ChatSession session = new ChatSession();
        session.setId(3L);
        ChatMessage assistantPrompt = new ChatMessage();
        assistantPrompt.setRole("assistant");
        assistantPrompt.setContent("可以为您办理购票。\n\n订票需要提供以下信息：");
        when(sessionService.requireSession(3L)).thenReturn(session);
        when(chatMemoryService.history(3L)).thenReturn(List.of(assistantPrompt));
        when(deepSeekClient.enabled()).thenReturn(true);
        when(deepSeekClient.stream(eq("你知道我叫什么吗"), anyList())).thenReturn(Flux.just("你还没有告诉我名字。"));

        StepVerifier.create(assistantService.stream(3L, "你知道我叫什么吗").collectList())
                .expectNextMatches(parts -> String.join("", parts).contains("名字"))
                .verifyComplete();

        verify(deepSeekClient).stream(eq("你知道我叫什么吗"), anyList());
        verify(localAssistantService, never()).answer(eq("你知道我叫什么吗"), anyList());
    }
}
