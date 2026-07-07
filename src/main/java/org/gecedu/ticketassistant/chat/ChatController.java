package org.gecedu.ticketassistant.chat;

import org.gecedu.ticketassistant.ai.AssistantService;
import org.gecedu.ticketassistant.common.ApiResponse;
import org.gecedu.ticketassistant.session.SessionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatMemoryService chatMemoryService;
    private final AssistantService assistantService;
    private final SessionService sessionService;

    public ChatController(ChatMemoryService chatMemoryService, AssistantService assistantService, SessionService sessionService) {
        this.chatMemoryService = chatMemoryService;
        this.assistantService = assistantService;
        this.sessionService = sessionService;
    }

    @GetMapping("/history/{sessionId}")
    public ApiResponse<List<ChatMessage>> history(@PathVariable Long sessionId) {
        sessionService.requireSession(sessionId);
        return ApiResponse.ok(chatMemoryService.history(sessionId));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam Long sessionId, @RequestParam String message) {
        return assistantService.stream(sessionId, message);
    }
}
