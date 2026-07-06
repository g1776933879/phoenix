package com.your.agent.core.remote;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * SSH 远程执行器 —— 参考 Hermes SSH 终端后端。
 * 在远程服务器上执行命令并返回结果。
 */
@Slf4j
public class SshExecutor {

    private final String host;
    private final int port;
    private final String user;
    private final String keyPath;
    private final long timeoutSeconds;

    public SshExecutor(String host, int port, String user, String keyPath, long timeoutSeconds) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.keyPath = keyPath;
        this.timeoutSeconds = timeoutSeconds;
        log.info("SshExecutor init: {}@{}:{}", user, host, port);
    }

    public SshResult execute(String command) {
        long start = System.currentTimeMillis();
        try {
            String[] cmd = {
                "ssh", "-o", "StrictHostKeyChecking=no",
                "-o", "ConnectTimeout=10",
                "-i", keyPath,
                "-p", String.valueOf(port),
                user + "@" + host,
                command
            };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line; while ((line = r.readLine()) != null) output.append(line).append("\n");
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;
            if (!finished) { process.destroyForcibly(); return new SshResult(false, "Timeout", output.toString(), elapsed); }

            int code = process.exitValue();
            return new SshResult(code == 0, "Exit: " + code, output.toString().trim(), elapsed);

        } catch (Exception e) {
            return new SshResult(false, e.getMessage(), "", System.currentTimeMillis() - start);
        }
    }

    public record SshResult(boolean success, String message, String output, long durationMs) {}
}