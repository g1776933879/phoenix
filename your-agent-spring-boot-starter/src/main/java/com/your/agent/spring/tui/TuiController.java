package com.your.agent.spring.tui;

import com.your.agent.core.loop.ReActEngine;
import com.your.agent.core.model.AgentResponse;
import com.your.agent.spring.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * TUI 终端界面 —— 参考 Hermes TUI / CLI 终端界面。
 * 提供全功能终端界面，支持多行编辑、命令历史、斜杠命令补全。
 */
@Slf4j
@RestController
@RequestMapping("/api/tui")
public class TuiController {

    private final ReActEngine reActEngine;
    private final SessionManager sessionManager;

    public TuiController(ReActEngine reActEngine, SessionManager sessionManager) {
        this.reActEngine = reActEngine;
        this.sessionManager = sessionManager;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        String sessionId = body.get("sessionId");
        if (content == null) return ResponseEntity.badRequest().body(Map.of("error", "content required"));

        if (sessionId != null) sessionManager.switchSession(sessionId);
        sessionManager.saveMessage("user", content, null);

        try {
            AgentResponse response = reActEngine.run(content);
            sessionManager.saveMessage("assistant", response.getFinalAnswer(), null);
            return ResponseEntity.ok(Map.of(
                "content", response.getFinalAnswer(),
                "sessionId", sessionManager.getCurrentSessionId(),
                "iterations", response.getIterations()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("content", "[Error] " + e.getMessage()));
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> sessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SessionManager.SessionSummary s : sessionManager.getSessions()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.id()); m.put("title", s.title()); m.put("count", s.messageCount());
            list.add(m);
        }
        return ResponseEntity.ok(list);
    }
}