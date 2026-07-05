package com.your.agent.spring.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 凤凰Agent框架的配置映射文件。
 * <p>
 * 对应 application.yml 中的 your.agent 配置前缀。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "your.agent")
public class AgentProperties {

    /** LLM模型配置 */
    private Llm llm = new Llm();

    /** ReAct引擎配置 */
    private ReAct react = new ReAct();

    /** 安全沙箱配置 */
    private Sandbox sandbox = new Sandbox();

    /** WebSocket配置 */
    private WebSocket websocket = new WebSocket();

    @Getter
    @Setter
    public static class Llm {
        /** 模型供应商：openai | ollama | azure */
        private String provider = "openai";

        /** API基础地址 */
        private String baseUrl = "https://api.openai.com/v1";

        /** API密钥 */
        private String apiKey = "";

        /** 模型名称 */
        private String modelName = "gpt-4o";

        /** 最大Token数 */
        private int maxTokens = 4096;

        /** 温度参数 */
        private double temperature = 0.7;

        /** 请求超时（秒） */
        private long timeoutSeconds = 60;
    }

    @Getter
    @Setter
    public static class ReAct {
        /** 最大迭代次数 */
        private int maxIterations = 10;

        /** 系统提示词文件路径 */
        private String systemPromptPath = "";
    }

    @Getter
    @Setter
    public static class Sandbox {
        /** 沙箱类型：none | docker */
        private String type = "none";

        /** Docker沙箱镜像名 */
        private String dockerImage = "ubuntu:22.04";

        /** Docker执行超时（秒） */
        private int timeoutSeconds = 30;
    }

    @Getter
    @Setter
    public static class WebSocket {
        /** WebSocket端点路径 */
        private String endpoint = "/ws/agent";

        /** 允许的源 */
        private String allowedOrigins = "*";
    }
}