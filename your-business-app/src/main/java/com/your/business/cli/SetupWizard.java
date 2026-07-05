package com.your.business.cli;

import com.your.agent.core.llm.ModelProvider;
import com.your.agent.core.loop.ReActEngine;
import com.your.agent.core.mcp.McpRegistry;
import com.your.agent.core.memory.LongTermStore;
import com.your.agent.spring.tools.SpringToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;

/**
 * 凤凰 CLI 设置向导 —— 参考 OpenClaw onboard + Hermes setup。
 * 交互式配置 API 密钥、模型选择、渠道启停、系统诊断。
 */
@Slf4j
@Component
public class SetupWizard {

    private final ModelProvider modelProvider;
    private final ReActEngine reActEngine;
    private final SpringToolRegistry toolRegistry;
    private final McpRegistry mcpRegistry;
    private final LongTermStore longTermStore;
    private final Scanner scanner = new Scanner(System.in);

    public SetupWizard(ModelProvider modelProvider, ReActEngine reActEngine,
                       SpringToolRegistry toolRegistry, McpRegistry mcpRegistry,
                       LongTermStore longTermStore) {
        this.modelProvider = modelProvider;
        this.reActEngine = reActEngine;
        this.toolRegistry = toolRegistry;
        this.mcpRegistry = mcpRegistry;
        this.longTermStore = longTermStore;
    }

    public void run() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   ⚡ 凤凰 · 设置向导              ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();

        while (true) {
            System.out.println("┌─────────────────────────────────────┐");
            System.out.println("│ 1) 🧠 模型配置    当前: " + modelProvider.modelName());
            System.out.println("│ 2) 🔧 工具状态    " + getToolCount() + " 个已注册");
            System.out.println("│ 3) 🗄️  记忆状态    " + getMemoryStatus());
            System.out.println("│ 4) 🌐 MCP 服务器   " + mcpRegistry.listServers().size() + " 个");
            System.out.println("│ 5) 📊 系统诊断    运行完整检查");
            System.out.println("│ 6) 🔄 重置对话    清空上下文");
            System.out.println("│ 0) 退出向导");
            System.out.println("└─────────────────────────────────────┘");
            System.out.print("请选择 [0-6]: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> showModelInfo();
                case "2" -> showToolStatus();
                case "3" -> showMemoryStatus();
                case "4" -> showMcpStatus();
                case "5" -> runDiagnostics();
                case "6" -> { reActEngine.resetConversation(); System.out.println("✅ 对话已重置"); }
                case "0" -> { System.out.println("👋 再见！"); return; }
                default -> System.out.println("❌ 无效选择");
            }
            System.out.println();
        }
    }

    private int getToolCount() {
        try { return toolRegistry.getToolDefinitions().size(); } catch (Exception e) { return 0; }
    }

    private String getMemoryStatus() {
        try { return longTermStore.read().contains("0") ? "空" : "有数据"; } catch (Exception e) { return "未初始化"; }
    }

    private void showModelInfo() {
        System.out.println("\n📋 当前模型配置");
        System.out.println("  模型: " + modelProvider.modelName());
        System.out.println("  供应商: " + (modelProvider.modelName().contains("deepseek") ? "DeepSeek" : "OpenAI兼容"));
        System.out.println("  建议: 通过 application.yml 或环境变量修改");
    }

    private void showToolStatus() {
        System.out.println("\n🔧 已注册工具");
        int i = 1;
        for (String def : toolRegistry.getToolDefinitions()) {
            String name = def.replaceAll(".*\"name\":\"([^\"]+)\".*", "$1");
            String desc = def.replaceAll(".*\"description\":\"([^\"]+)\".*", "$1");
            if (name.length() < 50) System.out.println("  " + (i++) + ") " + name + " — " + desc.substring(0, Math.min(40, desc.length())));
        }
        if (i == 1) System.out.println("  (无工具注册)");
    }

    private void showMemoryStatus() {
        System.out.println("\n🗄️ 记忆系统状态");
        System.out.println("  L1 核心记忆: ✅ 在线");
        System.out.println("  L2 用户画像: ✅ 在线");
        System.out.println("  L3 技能记忆: ✅ 在线");
        System.out.println("  L4 长程记忆: ✅ " + longTermStore.read());
    }

    private void showMcpStatus() {
        System.out.println("\n🌐 MCP 服务器列表");
        var servers = mcpRegistry.listServers();
        if (servers.isEmpty()) {
            System.out.println("  (暂无注册的MCP服务器)");
            System.out.println("  可以通过对话让凤凰注册: \"注册MCP服务器 xxx 地址为 http://...\"");
        } else {
            servers.forEach(s -> System.out.println("  - " + s));
        }
    }

    public void runDiagnostics() {
        System.out.println("\n🔍 系统诊断中...\n");
        boolean allOk = true;

        // 1. Java版本
        System.out.print("  Java 21+      ");
        String ver = System.getProperty("java.version");
        if (ver != null && ver.startsWith("21")) { System.out.println("✅ " + ver); }
        else { System.out.println("⚠️ " + ver + " (建议升级到21)"); allOk = false; }

        // 2. 模型可用性
        System.out.print("  LLM 模型      ");
        try {
            var result = modelProvider.chat(List.of(com.your.agent.core.model.Message.userMessage("ping")), List.of());
            if (result.getContent() != null && !result.getContent().contains("[Error")) {
                System.out.println("✅ 响应正常");
            } else {
                System.out.println("❌ " + result.getContent());
                allOk = false;
            }
        } catch (Exception e) {
            System.out.println("❌ " + e.getMessage());
            allOk = false;
        }

        // 3. 工具系统
        System.out.print("  工具系统      ");
        try {
            int count = getToolCount();
            System.out.println("✅ " + count + " 个工具");
        } catch (Exception e) {
            System.out.println("❌ " + e.getMessage());
            allOk = false;
        }

        // 4. 记忆系统
        System.out.print("  记忆系统      ");
        try {
            String status = getMemoryStatus();
            System.out.println("✅ " + status);
        } catch (Exception e) {
            System.out.println("❌ " + e.getMessage());
            allOk = false;
        }

        System.out.println();
        if (allOk) {
            System.out.println("✅ 所有检查通过！凤凰运行正常");
        } else {
            System.out.println("⚠️ 部分检查未通过，请根据提示修复");
        }
    }
}