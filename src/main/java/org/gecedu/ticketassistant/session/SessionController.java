package org.gecedu.ticketassistant.session;

import jakarta.validation.Valid;
import org.gecedu.ticketassistant.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/list")
    public ApiResponse<List<ChatSession>> list() {
        return ApiResponse.ok(sessionService.list());
    }

    @PostMapping
    public ApiResponse<ChatSession> create(@Valid @RequestBody SessionRequest request) {
        return ApiResponse.ok(sessionService.create(request.title()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ChatSession> update(@PathVariable Long id, @Valid @RequestBody SessionRequest request) {
        return ApiResponse.ok(sessionService.update(id, request.title()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sessionService.delete(id);
        return ApiResponse.ok(null);
    }
}
