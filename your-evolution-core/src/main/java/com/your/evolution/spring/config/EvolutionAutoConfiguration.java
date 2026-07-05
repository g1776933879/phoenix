package com.your.evolution.spring.config;

import com.your.agent.core.llm.ModelProvider;
import com.your.evolution.core.EvolutionEngine;
import com.your.evolution.core.skills.SkillFactory;
import com.your.evolution.spring.tools.SelfEvolutionTools;
import com.your.evolution.spring.tools.SkillCreationTools;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 凤凰自进化模块的Spring Boot自动配置。
 */
@Configuration
@ConditionalOnClass(EvolutionEngine.class)
public class EvolutionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EvolutionEngine.class)
    public EvolutionEngine evolutionEngine(ModelProvider modelProvider) {
        return new EvolutionEngine(modelProvider);
    }

    @Bean
    @ConditionalOnMissingBean(SelfEvolutionTools.class)
    public SelfEvolutionTools selfEvolutionTools(EvolutionEngine evolutionEngine) {
        return new SelfEvolutionTools(evolutionEngine);
    }

    @Bean
    @ConditionalOnMissingBean(SkillFactory.class)
    public SkillFactory skillFactory(ModelProvider modelProvider) {
        return new SkillFactory(modelProvider);
    }

    @Bean
    @ConditionalOnMissingBean(SkillCreationTools.class)
    public SkillCreationTools skillCreationTools(SkillFactory skillFactory) {
        return new SkillCreationTools(skillFactory);
    }
}