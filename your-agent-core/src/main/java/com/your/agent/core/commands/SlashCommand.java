package com.your.agent.core.commands;

import java.util.*;

/**
 * 斜杠命令定义 —— 参考 Codex CLI 的斜杠命令系统。
 * 支持 /help /reset /model /exec /skills /agents /clear /stats 等命令。
 */
public record SlashCommand(
    String name,
    String description,
    String usage,
    boolean requiresConfirmation,
    CommandExecutor executor
) {
    @FunctionalInterface
    public interface CommandExecutor {
        String execute(String args);
    }

    public static final Map<String, SlashCommand> COMMANDS = new LinkedHashMap<>();

    static {
        register(new SlashCommand("help", "显示所有可用命令", "/help [命令名]", false,
            args -> {
                StringBuilder sb = new StringBuilder("📋 可用命令:\n");
                COMMANDS.forEach((name, cmd) -> {
                    sb.append("  ").append(cmd.usage()).append("\n    ").append(cmd.description()).append("\n");
                });
                return sb.toString();
            }));

        register(new SlashCommand("reset", "重置当前对话上下文", "/reset", true,
            args -> "🔄 对话已重置"));

        register(new SlashCommand("model", "查看当前使用的模型", "/model [模型名]", false,
            args -> {
                if (args == null || args.trim().isEmpty()) {
                    return "🤖 当前模型: (通过环境变量配置)";
                }
                return "⚡ 模型切换请求已发送: " + args.trim();
            }));

        register(new SlashCommand("stats", "查看系统状态", "/stats", false,
            args -> "📊 系统状态\n  引擎: ReAct v2.0\n  记忆: L1-L4 四层\n  渠道: 4 个\n  状态: 运行中"));

        register(new SlashCommand("clear", "清空屏幕对话历史", "/clear", false,
            args -> "[CLEAR]"));

        register(new SlashCommand("skills", "列出所有已注册技能", "/skills", false,
            args -> "🔧 技能列表: 通过对话让永动机列出工具"));

        register(new SlashCommand("agents", "查看当前 AGENTS.md 配置", "/agents", false,
            args -> "📄 AGENTS.md 配置已加载"));

        register(new SlashCommand("exec", "执行 Shell 命令（需审批）", "/exec <命令>", true,
            args -> "[EXEC:" + args + "]"));

        register(new SlashCommand("export", "导出当前对话为 Markdown", "/export", false,
            args -> "[EXPORT]"));
    }

    public static void register(SlashCommand cmd) {
        COMMANDS.put(cmd.name(), cmd);
    }

    public static SlashCommand find(String name) {
        return COMMANDS.get(name.toLowerCase());
    }

    public static boolean isSlashCommand(String text) {
        return text != null && text.startsWith("/");
    }

    public static String getHelpText() {
        StringBuilder sb = new StringBuilder("📋 可用命令:\n");
        COMMANDS.forEach((name, cmd) -> {
            sb.append("  ").append(cmd.usage()).append("\n    ").append(cmd.description()).append("\n");
        });
        return sb.toString();
    }
}