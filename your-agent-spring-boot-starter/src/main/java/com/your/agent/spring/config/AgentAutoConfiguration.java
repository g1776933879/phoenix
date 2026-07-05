package com.your.agent.spring.config;

import com.your.agent.core.loop.ReActEngine;
import com.your.agent.core.mcp.McpRegistry;
import com.your.agent.core.memory.CoreMemory;
import com.your.agent.core.memory.LongTermStore;
import com.your.agent.core.memory.SkillMemory;
import com.your.agent.core.memory.UserProfile;
import com.your.agent.spring.gateway.WebSocketGateway;
import com.your.agent.spring.sandbox.ApprovalReviewer;
import com.your.agent.spring.sandbox.SandboxStrategy;
import com.your.agent.spring.tools.SpringToolRegistry;
import com.your.agent.core.llm.ModelProvider;
import com.your.agent.core.llm.OpenAiModelProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * 凤凰Agent的Spring Boot自动配置类。
 * <p>
 * 根据 your.agent.llm.provider 自动选择模型实现：
 * - "openai" → OpenAiModelProvider
 * - "ollama" → 使用OpenAiModelProvider（Ollama兼容OpenAI API）
 * 修复：移除对不存在的OllamaModelProvider的引用。
 */
@Configuration
@ConditionalOnClass(ReActEngine.class)
@EnableConfigurationProperties(AgentProperties.class)
public class AgentAutoConfiguration {

    private final AgentProperties properties;

    public AgentAutoConfiguration(AgentProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean(ModelProvider.class)
    public ModelProvider modelProvider() {
        AgentProperties.Llm llmConfig = properties.getLlm();
        String provider = llmConfig.getProvider().toLowerCase();

        // OpenAI兼容API（Ollama也使用此provider，因Ollama兼容OpenAI格式）
        if ("openai".equals(provider) || "ollama".equals(provider)) {
            return OpenAiModelProvider.builder()
                    .baseUrl(llmConfig.getBaseUrl())
                    .apiKey("ollama".equals(provider) ? "ollama" : llmConfig.getApiKey())
                    .modelName(llmConfig.getModelName())
                    .maxTokens(llmConfig.getMaxTokens())
                    .temperature(llmConfig.getTemperature())
                    .timeoutSeconds(llmConfig.getTimeoutSeconds())
                    .build();
        }

        throw new IllegalArgumentException(
                "Unsupported LLM provider: " + provider + ". Supported: openai, ollama");
    }

    @Bean
    @ConditionalOnMissingBean(SpringToolRegistry.class)
    public SpringToolRegistry springToolRegistry(ApplicationContext applicationContext) {
        return new SpringToolRegistry(applicationContext);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean(ReActEngine.class)
    public ReActEngine reActEngine(
            ModelProvider modelProvider,
            SpringToolRegistry toolRegistry
    ) {
        AgentProperties.ReAct reactConfig = properties.getReact();
        return new ReActEngine(
                modelProvider,
                toolRegistry,
                reactConfig.getMaxIterations(),
                null
        );
    }

    @Bean
    @ConditionalOnMissingBean(CoreMemory.class)
    public CoreMemory coreMemory() {
        return new CoreMemory();
    }

    @Bean
    @ConditionalOnMissingBean(UserProfile.class)
    public UserProfile userProfile() {
        return new UserProfile();
    }

    @Bean
    @ConditionalOnMissingBean(SkillMemory.class)
    public SkillMemory skillMemory() {
        return new SkillMemory();
    }

    @Bean
    @ConditionalOnMissingBean(LongTermStore.class)
    public LongTermStore longTermStore() {
        return new LongTermStore();
    }

    @Bean
    @ConditionalOnMissingBean(ApprovalReviewer.class)
    public ApprovalReviewer approvalReviewer() {
        return new ApprovalReviewer();
    }

    @Bean
    @ConditionalOnMissingBean(SandboxStrategy.class)
    @ConditionalOnProperty(prefix = "your.agent.sandbox", name = "type", havingValue = "docker")
    public SandboxStrategy dockerSandbox() {
        return command -> {
            throw new UnsupportedOperationException("Docker sandbox not yet implemented");
        };
    }

    @Bean
    @ConditionalOnMissingBean(McpRegistry.class)
    public McpRegistry mcpRegistry() {
        return new McpRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(WebSocketGateway.class)
    public WebSocketGateway webSocketGateway(ReActEngine engine) {
        return new WebSocketGateway(engine);
    }
}