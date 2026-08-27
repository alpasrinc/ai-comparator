package com.example.aicomparator.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.aicomparator.dto.DebateDetailResponse;
import com.example.aicomparator.dto.DebateRequest;
import com.example.aicomparator.dto.DebateSummaryResponse;
import com.example.aicomparator.service.DebateOrchestrator;
import com.example.aicomparator.service.DebateService;

@RestController
@RequestMapping("/api/debates")
public class DebateController {

    private final DebateOrchestrator debateOrchestrator;
    private final DebateService debateService;
    private final long sseTimeoutMillis;

    public DebateController(
            DebateOrchestrator debateOrchestrator,
            DebateService debateService,
            @Value("${ai.request-timeout-seconds:30}")
            long requestTimeoutSeconds
    ) {
        this.debateOrchestrator = debateOrchestrator;
        this.debateService = debateService;
        // Münazara birden çok turu sıralı çalıştırır; SSE timeout'u
        // (tur sayısı + sentez) için cömert tut: 6 * (istek zaman aşımı) + 15s.
        this.sseTimeoutMillis = (requestTimeoutSeconds * 6 + 15) * 1000L;
    }

    @PostMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter startDebate(@Valid @RequestBody DebateRequest request) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMillis);
        debateOrchestrator.runDebate(request, emitter);
        return emitter;
    }

    @GetMapping
    public List<DebateSummaryResponse> listDebates() {
        return debateService.listDebates();
    }

    @GetMapping("/{id}")
    public DebateDetailResponse getDebate(@PathVariable Long id) {
        return debateService.getDebate(id);
    }
}
