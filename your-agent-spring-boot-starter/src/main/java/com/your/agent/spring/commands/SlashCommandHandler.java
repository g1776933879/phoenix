package com.your.agent.spring.commands;

import com.your.agent.core.commands.SlashCommand;
import com.your.agent.core.loop.ReActEngine;
import com.your.agent.core.memory.AgentsConfig;
import com.your.agent.spring.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 斜杠命令处理器 —— 处理所有 / 开头的命令。
 * 参考 Codex CLI 的斜杠命令系统。
 */
@Slf4j
@RestController
@RequestMapping("/api/commands")
public class SlashCommandHandler {

    private final ReActEngine reActEngine;
    private final SessionManager sessionManager;
    private final AgentsConfig agentsConfig;

    public SlashCommandHandler(ReActEngine reActEngine, SessionManager sessionManager, AgentsConfig agentsConfig) {
        this.reActEngine = reActEngine;
        this.sessionManager = sessionManager;
        this.agentsConfig = agentsConfig;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleCommand(@RequestBody Map<String, String> request) {
        String text = request.get("command");
        if (text == null || !text.startsWith("/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not a slash command"));
        }

        String[] parts = text.substring(1).split("\\s+", 2);
        String cmdName = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        SlashCommand cmd = SlashCommand.find(cmdName);
        if (cmd == null) {
            return ResponseEntity.ok(Map.of("content", "❌ 未知命令: /" + cmdName + "\n发送 /help 查看可用命令"));
        }

        // 执行命令
        String result = cmd.executor().execute(args);

        // 处理后置操作
        switch (cmdName) {
            case "reset" -> {
                reActEngine.resetConversation();
                result = "🔄 对话已重置";
            }
            case "clear" -> {
                // 前端处理
            }
            case "agents" -> {
                result = "📄 **AGENTS.md**\n路径: `" + agentsConfig.getConfigPath() + "`\n\n```markdown\n" + agentsConfig.getContent() + "\n```";
            }
        }

        // 记录到会话
        sessionManager.saveMessage("user", text, null);
        sessionManager.saveMessage("assistant", result, null);

        return ResponseEntity.ok(Map.of("content", result));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listCommands() {
        return ResponseEntity.ok(Map.of("commands", SlashCommand.getHelpText()));
    }
}