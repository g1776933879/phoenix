package com.your.agent.spring.subagent;

import com.your.agent.core.subagent.SubAgentManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/subagents")
public class SubAgentController {

    private final SubAgentManager subAgentManager;

    public SubAgentController(SubAgentManager subAgentManager) {
        this.subAgentManager = subAgentManager;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String task = body.get("task");
        if (name == null || task == null)
            return ResponseEntity.badRequest().body(Map.of("error", "name, task required"));

        String id = subAgentManager.createAgent(name, task, body.get("systemPrompt"));
        subAgentManager.executeAgent(id);
        return ResponseEntity.ok(Map.of("id", id, "name", name, "status", "running"));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(subAgentManager.listAgents().stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.id()); m.put("name", a.name()); m.put("status", a.status().name());
            m.put("result", a.result() != null && a.result().length() > 200 ? a.result().substring(0, 200) + "..." : a.result());
            return m;
        }).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        var a = subAgentManager.getAgent(id);
        if (a == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
            "id", a.id(), "name", a.name(), "task", a.task(),
            "status", a.status().name(), "result", a.result()
        ));
    }
}