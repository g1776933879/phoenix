package com.your.agent.spring.cron;

import com.your.agent.core.cron.CronScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/cron")
public class CronController {

    private final CronScheduler cronScheduler;

    public CronController(CronScheduler cronScheduler) {
        this.cronScheduler = cronScheduler;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> addJob(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String prompt = body.get("prompt");
        String schedule = body.get("schedule");
        if (name == null || prompt == null || schedule == null)
            return ResponseEntity.badRequest().body(Map.of("error", "name, prompt, schedule required"));

        String id = cronScheduler.addNaturalJob(name, prompt, schedule, "web");
        return ResponseEntity.ok(Map.of("id", id, "name", name, "status", "created"));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listJobs() {
        return ResponseEntity.ok(cronScheduler.listJobs().stream().map(j -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", j.id()); m.put("name", j.name()); m.put("status", j.status().name());
            m.put("cronExpr", j.cronExpr()); m.put("history", j.history());
            return m;
        }).collect(Collectors.toList()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteJob(@PathVariable String id) {
        cronScheduler.deleteJob(id);
        return ResponseEntity.ok(Map.of("message", "Job deleted"));
    }
}