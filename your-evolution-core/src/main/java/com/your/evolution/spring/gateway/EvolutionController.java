package com.your.evolution.spring.gateway;

import com.your.evolution.core.EvolutionEngine;
import com.your.evolution.core.EvolutionReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 自进化REST端点 —— 通过HTTP触发凤凰的自我进化。
 */
@Slf4j
@RestController
@RequestMapping("/api/evolution")
public class EvolutionController {

    private final EvolutionEngine evolutionEngine;

    public EvolutionController(EvolutionEngine evolutionEngine) {
        this.evolutionEngine = evolutionEngine;
        log.info("EvolutionController initialized at /api/evolution");
    }

    @PostMapping("/evolve")
    public ResponseEntity<Map<String, Object>> evolve() {
        log.info("🧬 Manual evolution triggered via REST");
        long start = System.currentTimeMillis();
        EvolutionReport report = evolutionEngine.evolve();
        long elapsed = System.currentTimeMillis() - start;

        return ResponseEntity.ok(Map.of(
                "success", report.isHasChanges(),
                "report", report.toMarkdown(),
                "totalIssues", report.getTotalIssues(),
                "patchesApplied", report.getPatchesApplied(),
                "patchesFailed", report.getPatchesFailed(),
                "durationMs", elapsed,
                "hasChanges", report.isHasChanges()
        ));
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze() {
        log.info("🔍 Code analysis triggered via REST");
        String analysis = evolutionEngine.analyzeOnly();
        return ResponseEntity.ok(Map.of("analysis", analysis));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "module", "your-evolution-core",
                "status", "READY",
                "capabilities", "evolve | analyze | self_read_source | self_count_lines"
        ));
    }
}