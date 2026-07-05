package com.your.agent.spring.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.your.agent.core.model.ToolCall;
import com.your.agent.core.tool.Tool;
import com.your.agent.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于Spring上下文的工具注册中心实现。
 * 修复：使用@Lazy延迟初始化，避免循环依赖。
 */
@Slf4j
@Lazy
public class SpringToolRegistry implements ToolRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOOL_TEMPLATE = "{\"type\":\"function\",\"function\":{\"name\":\"%s\",\"description\":\"%s\",\"parameters\":%s}}";

    private final Map<String, ToolMethod> toolMap = new ConcurrentHashMap<>();
    private final List<String> toolDefinitions = new ArrayList<>();

    private volatile boolean initialized = false;
    private final ApplicationContext applicationContext;

    public SpringToolRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        // 延迟初始化，不在构造器中触发循环依赖
        log.debug("SpringToolRegistry created (lazy init deferred)");
    }

    /**
     * 延迟初始化扫描
     */
    private void ensureInitialized() {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            scanTools();
            initialized = true;
        }
    }

    private void scanTools() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        int count = 0;

        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> beanClass = bean.getClass();

            for (Method method : beanClass.getDeclaredMethods()) {
                Tool annotation = AnnotationUtils.findAnnotation(method, Tool.class);
                if (annotation == null) continue;

                String toolName = annotation.name();
                method.setAccessible(true);

                toolMap.put(toolName, new ToolMethod(bean, method, annotation.description(), annotation.requireApproval()));

                String toolDef = String.format(TOOL_TEMPLATE,
                        escapeJson(toolName),
                        escapeJson(annotation.description()),
                        annotation.parametersSchema());
                toolDefinitions.add(toolDef);

                log.info("Registered tool: {} -> {}.{} (approval={})",
                        toolName, beanClass.getSimpleName(), method.getName(), annotation.requireApproval());
                count++;
            }
        }

        log.info("SpringToolRegistry initialized: {} tools registered", count);
    }

    @Override
    public String execute(ToolCall toolCall) throws Exception {
        ensureInitialized();
        ToolMethod tm = toolMap.get(toolCall.getName());
        if (tm == null) {
            throw new IllegalArgumentException("Tool not found: " + toolCall.getName());
        }

        Method method = tm.method();
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        Map<String, Object> callArgs = toolCall.getArguments() != null
                ? toolCall.getArguments()
                : Collections.emptyMap();

        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            if (paramTypes.length == 1 && paramType.equals(String.class)) {
                args[i] = MAPPER.writeValueAsString(callArgs);
            } else if (paramType.equals(String.class)) {
                args[i] = callArgs.values().stream().findFirst()
                        .map(Object::toString)
                        .orElse("");
            } else {
                args[i] = MAPPER.convertValue(callArgs, paramType);
            }
        }

        log.debug("Invoking tool: {}", toolCall.getName());
        Object result = method.invoke(tm.bean(), args);
        return result != null ? result.toString() : "OK (void)";
    }

    @Override
    public List<String> getToolDefinitions() {
        ensureInitialized();
        return new ArrayList<>(toolDefinitions);
    }

    @Override
    public boolean requiresApproval(String toolName) {
        ensureInitialized();
        ToolMethod tm = toolMap.get(toolName);
        return tm != null && tm.requireApproval();
    }

    private record ToolMethod(Object bean, Method method, String description, boolean requireApproval) {}

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}