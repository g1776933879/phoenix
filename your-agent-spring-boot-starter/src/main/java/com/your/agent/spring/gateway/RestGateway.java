package com.your.agent.spring.gateway;

import com.your.agent.core.loop.ReActEngine;
import com.your.agent.core.model.AgentResponse;
import com.your.agent.core.llm.StreamCallback;
import com.your.agent.spring.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/api")
public class RestGateway {

    private final ReActEngine reActEngine;
    private final SessionManager sessionManager;
    private final ExecutorService executor;

    public RestGateway(@Lazy ReActEngine reActEngine, SessionManager sessionManager) {
        this.reActEngine = reActEngine;
        this.sessionManager = sessionManager;
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("sse-", 0).factory());
        log.info("RestGateway+Sessions initialized");
    }

    // ===== 对话 =====
    @PostMapping("/agent/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        if (content == null || content.trim().isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
        long start = System.currentTimeMillis();
        try {
            sessionManager.saveMessage("user", content, null);
            AgentResponse response = reActEngine.run(content);
            // 持久化保存对话
            for (String step : response.getIntermediateSteps()) {
                if (step.startsWith("[Action]")) sessionManager.saveMessage("tool", step, null);
            }
            sessionManager.saveMessage("assistant", response.getFinalAnswer(), null);
            return ResponseEntity.ok(Map.of(
                    "content", response.getFinalAnswer(),
                    "iterations", response.getIterations(),
                    "durationMs", response.getDurationMs(),
                    "truncated", response.isTruncated(),
                    "sessionId", sessionManager.getCurrentSessionId()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    // ===== 会话管理 =====
    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> listSessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SessionManager.SessionSummary s : sessionManager.getSessions()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.id()); m.put("title", s.title()); m.put("createdAt", s.createdAt());
            m.put("messageCount", s.messageCount());
            m.put("current", s.id().equals(sessionManager.getCurrentSessionId()));
            list.add(m);
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<?> getSession(@PathVariable String id) {
        var msgs = sessionManager.getSessionMessages(id);
        if (msgs.isEmpty()) return ResponseEntity.notFound().build();
        sessionManager.switchSession(id);
        return ResponseEntity.ok(Map.of("sessionId", id, "messages", msgs));
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, String>> newSession() {
        String id = sessionManager.createNewSession();
        reActEngine.resetConversation();
        return ResponseEntity.ok(Map.of("sessionId", id, "message", "新会话已创建"));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable String id) {
        sessionManager.deleteSession(id);
        return ResponseEntity.ok(Map.of("message", "会话已删除"));
    }

    @GetMapping("/agent/chat/sse")
    public SseEmitter chatStream(@RequestParam("content") String content) {
        SseEmitter emitter = new SseEmitter(1800000L);
        AtomicBoolean completed = new AtomicBoolean(false);
        sessionManager.saveMessage("user", content, null);
        executor.execute(() -> {
            try {
                reActEngine.runStream(content, new StreamCallback() {
                    public void onNext(String chunk, boolean done) {
                        if (completed.get()) return;
                        try { emitter.send(SseEmitter.event().name("chunk").data(chunk));
                            if (done && completed.compareAndSet(false, true)) {
                                emitter.send(SseEmitter.event().name("done").data("{\"status\":\"complete\"}"));
                                emitter.complete();
                            }
                        } catch (Exception ignored) {}
                    }
                    public void onComplete() {
                        if (completed.compareAndSet(false, true)) {
                            try { emitter.send(SseEmitter.event().name("done").data("{\"status\":\"complete\"}")); emitter.complete(); }
                            catch (Exception ignored) {}
                        }
                    }
                    public void onError(Throwable error) {
                        if (completed.get()) return;
                        completed.set(true);
                        try { emitter.completeWithError(error); } catch (Exception ignored) {}
                    }
                });
            } catch (Exception e) {
                if (completed.compareAndSet(false, true)) { try { emitter.completeWithError(e); } catch (Exception ignored) {} }
            }
        });
        emitter.onTimeout(() -> log.warn("SSE timeout"));
        return emitter;
    }

    @PostMapping("/agent/reset")
    public ResponseEntity<Map<String, String>> reset() {
        reActEngine.resetConversation();
        return ResponseEntity.ok(Map.of("message", "Conversation reset"));
    }

    @GetMapping("/agent/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "engine", "ReActEngine", "streaming", true,
                "timestamp", System.currentTimeMillis(), "sessionId", sessionManager.getCurrentSessionId()));
    }
}