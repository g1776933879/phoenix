package com.your.agent.spring.model;

import com.your.agent.core.llm.OpenAiModelProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/model")
public class ModelController {

    @GetMapping
    public ResponseEntity<Map<String, String>> getModel() {
        String[] current = OpenAiModelProvider.getCurrentModel();
        return ResponseEntity.ok(Map.of("model", current[0], "baseUrl", current[1]));
    }

    @PostMapping("/switch")
    public ResponseEntity<Map<String, Object>> switchModel(@RequestBody Map<String, String> body) {
        String model = body.get("model");
        String baseUrl = body.get("baseUrl");
        if (model == null || model.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "model name required"));
        }
        OpenAiModelProvider.switchModel(model, baseUrl);
        log.info("Model switched via API: {} @ {}", model, baseUrl);
        return ResponseEntity.ok(Map.of("status", "switched", "model", model, "baseUrl", baseUrl != null ? baseUrl : ""));
    }
}