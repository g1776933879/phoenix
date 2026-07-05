package com.your.business.cli;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * CLI模式的最小Spring配置 —— 不启动Web服务器，只加载核心Bean。
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.your.agent", "com.your.business", "com.your.evolution"
})
public class CliConfig {
}