package com.your.business.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 凤凰 CLI 入口 —— 支持 setup / doctor / chat 等命令。
 */
public class CliEntryPoint {

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "setup";

        switch (command) {
            case "setup" -> runSetup();
            case "doctor" -> runDoctor();
            case "chat" -> runChat();
            default -> System.out.println("未知命令: " + command + ". 可用: setup, doctor, chat");
        }
    }

    static void runSetup() {
        System.setProperty("spring.main.web-application-type", "none");
        try (ConfigurableApplicationContext ctx = SpringApplication.run(CliConfig.class)) {
            SetupWizard wizard = ctx.getBean(SetupWizard.class);
            wizard.run();
        }
    }

    static void runDoctor() {
        System.setProperty("spring.main.web-application-type", "none");
        try (ConfigurableApplicationContext ctx = SpringApplication.run(CliConfig.class)) {
            SetupWizard wizard = ctx.getBean(SetupWizard.class);
            wizard.runDiagnostics();
        }
    }

    static void runChat() {
        System.out.println("💬 终端对话模式: 执行 bash chat.sh");
    }
}