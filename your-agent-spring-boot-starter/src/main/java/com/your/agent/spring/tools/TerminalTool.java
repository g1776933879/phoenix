package com.your.agent.spring.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.your.agent.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 终端命令执行工具 —— 在本地系统上执行Shell命令。
 * 修复：统一JSON解析，移除冗余判断。
 */
@Slf4j
@Component
public class TerminalTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Tool(
        name = "execute_command",
        description = "在Linux/Unix终端上执行任意Shell命令并返回输出结果。" +
                      "支持管道、重定向等标准Shell语法。" +
                      "注意：此工具可执行任意系统命令，请谨慎使用。",
        parametersSchema = "{\"type\":\"object\",\"properties\":{" +
                "\"command\":{\"type\":\"string\",\"description\":\"要执行的Shell命令\"}," +
                "\"timeoutSeconds\":{\"type\":\"integer\",\"description\":\"超时秒数，默认30\"}" +
                "},\"required\":[\"command\"]}",
        requireApproval = true
    )
    public String executeCommand(String argsJson) {
        try {
            var node = MAPPER.readTree(argsJson);
            String command = node.get("command").asText();
            int timeoutSeconds = node.has("timeoutSeconds") ? node.get("timeoutSeconds").asInt(30) : 30;

            log.info("Executing command: {} (timeout={}s)", command, timeoutSeconds);

            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "[Timeout] Command exceeded " + timeoutSeconds + " seconds";
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            log.info("Command completed: exitCode={}, outputLength={}", exitCode, result.length());
            return String.format("Exit code: %d\nOutput:\n%s", exitCode, result.isEmpty() ? "(empty)" : result);

        } catch (Exception e) {
            log.error("Command execution failed", e);
            return "[Error] " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}