package com.your.agent.core.subagent;

import com.your.agent.core.loop.ReActEngine;
import com.your.agent.core.model.AgentResponse;
import com.your.agent.core.model.Message;
import com.your.agent.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 子代理系统 —— 参考 Hermes 的子代理并行架构。
 * 支持创建隔离的子代理来并行处理复杂任务。
 * 每个子代理有独立的 ReActEngine 实例和上下文。
 */
@Slf4j
public class SubAgentManager {

    private final ToolRegistry toolRegistry;
    private final Map<String, SubAgent> agents = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SubAgentManager(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        log.info("SubAgentManager initialized");
    }

    /**
     * 创建一个子代理
     */
    public String createAgent(String name, String task, String systemPrompt) {
        String id = "sub_" + idCounter.incrementAndGet();
        SubAgent agent = new SubAgent(id, name, task, systemPrompt, SubAgentStatus.CREATED);
        agents.put(id, agent);
        log.info("SubAgent created: {} ({})", name, id);
        return id;
    }

    /**
     * 异步执行子代理
     */
    public CompletableFuture<String> executeAgent(String agentId) {
        SubAgent agent = agents.get(agentId);
        if (agent == null) {
            return CompletableFuture.completedFuture("[Error] SubAgent not found: " + agentId);
        }

        agents.put(agentId, agent.withStatus(SubAgentStatus.RUNNING));

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 创建独立的 ReActEngine
                var engine = new ReActEngine(null, toolRegistry, 10,
                        agent.systemPrompt() != null ? agent.systemPrompt() :
                                "你是子代理 " + agent.name() + "，任务是：" + agent.task());

                // 执行任务
                var response = engine.run(agent.task());
                String result = response.getFinalAnswer();

                agents.put(agentId, agent.withStatus(SubAgentStatus.COMPLETED).withResult(result));
                log.info("SubAgent {} completed: {} chars", agentId, result.length());
                return result;

            } catch (Exception e) {
                log.error("SubAgent {} failed", agentId, e);
                agents.put(agentId, agent.withStatus(SubAgentStatus.FAILED).withResult("[Error] " + e.getMessage()));
                return "[Error] " + e.getMessage();
            }
        }, executor);
    }

    /**
     * 并行执行多个子代理
     */
    public Map<String, CompletableFuture<String>> executeAll(List<String> agentIds) {
        Map<String, CompletableFuture<String>> futures = new HashMap<>();
        for (String id : agentIds) {
            futures.put(id, executeAgent(id));
        }
        return futures;
    }

    /**
     * 等待所有子代理完成
     */
    public Map<String, String> waitForAll(Map<String, CompletableFuture<String>> futures, long timeoutMs) {
        Map<String, String> results = new HashMap<>();
        try {
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Wait for all subagents interrupted", e);
        }

        futures.forEach((id, future) -> {
            try {
                results.put(id, future.get(1, TimeUnit.SECONDS));
            } catch (Exception e) {
                results.put(id, "[Timeout]");
            }
        });

        return results;
    }

    public SubAgent getAgent(String id) { return agents.get(id); }
    public List<SubAgent> listAgents() { return new ArrayList<>(agents.values()); }

    public enum SubAgentStatus { CREATED, RUNNING, COMPLETED, FAILED }

    public record SubAgent(String id, String name, String task, String systemPrompt,
                           SubAgentStatus status, String result, long createdAt) {
        public SubAgent {
            if (result == null) result = "";
        }

        public SubAgent(String id, String name, String task, String systemPrompt, SubAgentStatus status) {
            this(id, name, task, systemPrompt, status, "", System.currentTimeMillis());
        }

        public SubAgent withStatus(SubAgentStatus newStatus) {
            return new SubAgent(id, name, task, systemPrompt, newStatus, result, createdAt);
        }

        public SubAgent withResult(String newResult) {
            return new SubAgent(id, name, task, systemPrompt, status, newResult, createdAt);
        }
    }
}