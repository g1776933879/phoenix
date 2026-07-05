package com.your.agent.core.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具注解 —— 标记一个方法为Agent可调用的工具。
 * <p>
 * 被标注的方法会被ToolRegistry扫描并注册，
 * 在ReAct循环中LLM可以通过Function Calling调用它。
 * <p>
 * 示例用法：
 * <pre>
 * &#64;Tool(
 *     name = "search_db",
 *     description = "搜索数据库中的用户信息",
 *     parametersSchema = "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\"}}}",
 *     requireApproval = true
 * )
 * public String searchDb(String keyword) { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {

    /**
     * 工具名称（唯一标识，LLM通过此名字调用）
     */
    String name();

    /**
     * 工具描述（给LLM理解工具用途，越详细越好）
     */
    String description();

    /**
     * 参数JSON Schema定义
     * 格式遵循OpenAI Function Calling规范的parameters字段
     */
    String parametersSchema();

    /**
     * 是否需要人工审批
     * 敏感操作（如删除数据、执行命令）建议设为true
     */
    boolean requireApproval() default false;
}