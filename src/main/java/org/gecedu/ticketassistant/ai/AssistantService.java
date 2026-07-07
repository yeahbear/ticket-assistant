package org.gecedu.ticketassistant.ai;

import org.gecedu.ticketassistant.chat.ChatMemoryService;
import org.gecedu.ticketassistant.chat.ChatMessage;
import org.gecedu.ticketassistant.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final SessionService sessionService;
    private final ChatMemoryService chatMemoryService;
    private final LocalAssistantService localAssistantService;
    private final DeepSeekClient deepSeekClient;
    private final boolean localFallback;

    public AssistantService(
            SessionService sessionService,
            ChatMemoryService chatMemoryService,
            LocalAssistantService localAssistantService,
            DeepSeekClient deepSeekClient,
            @Value("${ai.deepseek.local-fallback:true}") boolean localFallback
    ) {
        this.sessionService = sessionService;
        this.chatMemoryService = chatMemoryService;
        this.localAssistantService = localAssistantService;
        this.deepSeekClient = deepSeekClient;
        this.localFallback = localFallback;
    }

    public Flux<String> stream(Long sessionId, String message) {
        sessionService.requireSession(sessionId);
        String cleanMessage = message == null ? "" : message.trim();
        if (cleanMessage.isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        List<ChatMessage> history = chatMemoryService.history(sessionId);
        chatMemoryService.save(sessionId, "user", cleanMessage);

        if (shouldUseLocal(cleanMessage, history)) {
            return localStream(sessionId, cleanMessage, history);
        }

        StringBuilder answer = new StringBuilder();
        AtomicBoolean saved = new AtomicBoolean(false);
        Flux<String> deepSeekStream = deepSeekClient.stream(cleanMessage, history)
                .doOnNext(answer::append)
                .doOnComplete(() -> saveAssistantMessage(sessionId, answer.toString(), saved));

        return localFallback ? deepSeekStream.onErrorResume(ex -> {
            log.warn("DeepSeek stream failed, fallback to local assistant: {}", ex.getMessage());
            return localStream(sessionId, cleanMessage, history);
        }) : deepSeekStream;
    }

    private boolean shouldUseLocal(String message, List<ChatMessage> history) {
        String latestAssistant = latestAssistant(history);
        boolean waitingForBookingInfo = latestAssistant.contains("订票需要提供") || latestAssistant.contains("生成购票订单");
        boolean waitingForRefundInfo = latestAssistant.contains("退票有两种方式") || latestAssistant.contains("请选择要退票的订单号");
        boolean bookingContinuation = waitingForBookingInfo && IntentParser.hasBookingInfo(message);
        boolean refundContinuation = waitingForRefundInfo && (IntentParser.hasRefundInfo(message) || IntentParser.isSelectionReply(message));
        boolean toolIntent = IntentParser.isWeatherIntent(message)
                || IntentParser.isOrderQueryIntent(message)
                || IntentParser.isRefundIntent(message)
                || IntentParser.isBookingIntent(message)
                || bookingContinuation
                || refundContinuation;
        return toolIntent || !deepSeekClient.enabled();
    }

    private String latestAssistant(List<ChatMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if ("assistant".equals(message.getRole())) {
                return message.getContent();
            }
        }
        return "";
    }

    private Flux<String> localStream(Long sessionId, String message, List<ChatMessage> history) {
        return Mono.fromCallable(() -> localAssistantService.answer(message, history))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(answer -> toStream(answer)
                        .doOnComplete(() -> chatMemoryService.save(sessionId, "assistant", answer)));
    }

    private Flux<String> toStream(String answer) {
        List<String> pieces = answer.chars()
                .mapToObj(code -> String.valueOf((char) code))
                .toList();
        return Flux.fromIterable(pieces).delayElements(Duration.ofMillis(12));
    }

    private void saveAssistantMessage(Long sessionId, String answer, AtomicBoolean saved) {
        if (saved.compareAndSet(false, true) && !answer.isBlank()) {
            chatMemoryService.save(sessionId, "assistant", answer);
        }
    }
}
