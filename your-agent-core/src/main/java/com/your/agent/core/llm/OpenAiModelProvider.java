package com.your.agent.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.your.agent.core.model.Message;
import com.your.agent.core.model.ToolCall;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class OpenAiModelProvider implements ModelProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile String CURRENT_MODEL = "gpt-4o";
    private static volatile String CURRENT_BASE_URL = "https://api.openai.com/v1";

    private final OkHttpClient httpClient;
    private final OkHttpClient sseHttpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String modelNameInput;
    private final int maxTokens;
    private final double temperature;
    private static final AtomicInteger KEY_INDEX = new AtomicInteger(0);
    private static final List<String> KEY_CHAIN = new ArrayList<>();

    static {
        String k1 = System.getenv("SENSENOVA_API_KEY");
        String k2 = System.getenv("PHOENIX_API_KEY_2");
        String k3 = System.getenv("PHOENIX_API_KEY_3");
        String k4 = System.getenv("PHOENIX_API_KEY_4");
        String k5 = System.getenv("PHOENIX_API_KEY_5");
        if (k1 != null && !k1.isEmpty()) KEY_CHAIN.add(k1);
        if (k2 != null && !k2.isEmpty()) KEY_CHAIN.add(k2);
        if (k3 != null && !k3.isEmpty()) KEY_CHAIN.add(k3);
        if (k4 != null && !k4.isEmpty()) KEY_CHAIN.add(k4);
        if (k5 != null && !k5.isEmpty()) KEY_CHAIN.add(k5);
    }

    private String getEffectiveKey() {
        if (KEY_CHAIN.isEmpty()) return apiKey;
        int idx = KEY_INDEX.get() % KEY_CHAIN.size();
        String key = KEY_CHAIN.get(idx);
        if (idx > 0) log.info("Using key {} of {} (index={})", idx + 1, KEY_CHAIN.size(), idx);
        return key;
    }

    @Builder
    public OpenAiModelProvider(String baseUrl, String apiKey, String modelName, int maxTokens, double temperature, long timeoutSeconds) {
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";
        this.apiKey = apiKey;
        this.modelNameInput = modelName != null ? modelName : "gpt-4o";
        this.maxTokens = maxTokens > 0 ? maxTokens : 4096;
        this.temperature = temperature > 0 ? temperature : 0.7;
        long timeout = timeoutSeconds > 0 ? timeoutSeconds : 60;
        this.httpClient = new OkHttpClient.Builder().connectTimeout(timeout, TimeUnit.SECONDS).readTimeout(timeout, TimeUnit.SECONDS).writeTimeout(timeout, TimeUnit.SECONDS).build();
        this.sseHttpClient = new OkHttpClient.Builder().connectTimeout(timeout, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).writeTimeout(timeout, TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
        CURRENT_MODEL = this.modelNameInput;
        CURRENT_BASE_URL = this.baseUrl;
        log.info("OpenAiModelProvider initialized: model={}, baseUrl={}, streaming=true", this.modelNameInput, this.baseUrl);
    }

    /** 运行时切换模型，不用重启 */
    public static void switchModel(String modelName, String baseUrl) {
        if (modelName != null && !modelName.isEmpty()) CURRENT_MODEL = modelName;
        if (baseUrl != null && !baseUrl.isEmpty()) CURRENT_BASE_URL = baseUrl;
        log.info("Switched model to: {} @ {}", CURRENT_MODEL, CURRENT_BASE_URL);
    }

    public static String[] getCurrentModel() {
        return new String[]{CURRENT_MODEL, CURRENT_BASE_URL};
    }

    @Override
    public Message chat(List<Message> messages, List<String> tools) {
        int maxRetries = KEY_CHAIN.size() > 1 ? KEY_CHAIN.size() : 1;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String jsonBody = MAPPER.writeValueAsString(buildRequestBody(messages, tools, false));
                Request request = new Request.Builder().url(baseUrl + "/chat/completions").addHeader("Content-Type", "application/json").addHeader("Authorization", "Bearer " + getEffectiveKey()).post(RequestBody.create(jsonBody, JSON)).build();
                log.debug("Sending sync request: model={}, messages={}", modelNameInput, messages.size());
                long start = System.currentTimeMillis();
                try (Response response = httpClient.newCall(request).execute()) {
                    long elapsed = System.currentTimeMillis() - start;
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "null";
                        if (response.code() == 429 && (attempt + 1) < maxRetries) {
                            KEY_INDEX.incrementAndGet();
                            log.warn("Key 429 on attempt {}, switching to key {}", attempt + 1, KEY_INDEX.get() % KEY_CHAIN.size());
                            continue;
                        }
                        log.error("LLM API error: status={}, body={}", response.code(), errorBody);
                        return Message.builder().role("assistant").content("[LLM API Error: HTTP " + response.code() + "]").build();
                    }
                    String responseBody = response.body() != null ? response.body().string() : "";
                    log.debug("LLM sync response in {}ms: {} chars", elapsed, responseBody.length());
                    return parseResponse(responseBody);
                }
            } catch (IOException e) {
                if ((attempt + 1) < maxRetries) {
                    KEY_INDEX.incrementAndGet();
                    log.warn("Network error on attempt {}, switching key", attempt + 1);
                    continue;
                }
                log.error("LLM communication failed", e);
                return Message.builder().role("assistant").content("[Network Error: " + e.getMessage() + "]").build();
            }
        }
        return Message.builder().role("assistant").content("All API keys exhausted").build();
    }

    @Override
    public void chatStream(List<Message> messages, List<String> tools, StreamCallback callback) {
        int maxRetries = KEY_CHAIN.size() > 1 ? KEY_CHAIN.size() : 1;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String jsonBody = MAPPER.writeValueAsString(buildRequestBody(messages, tools, true));
                Request request = new Request.Builder().url(baseUrl + "/chat/completions").addHeader("Content-Type", "application/json").addHeader("Authorization", "Bearer " + getEffectiveKey()).post(RequestBody.create(jsonBody, JSON)).build();
                log.debug("Sending streaming request: model={}, messages={}", modelNameInput, messages.size());
                AtomicReference<Map<String, StringBuilder>> toolCallBuffers = new AtomicReference<>(new HashMap<>());
                AtomicReference<String> finishReason = new AtomicReference<>(null);
                AtomicBoolean keyExhausted = new AtomicBoolean(false);
                EventSourceListener listener = new EventSourceListener() {
                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    if ("[DONE]".equals(data)) {
                        Map<String, StringBuilder> buffers = toolCallBuffers.get();
                        if (!buffers.isEmpty()) {
                            StringBuilder aggregated = new StringBuilder("__TOOL_CALLS__:");
                            for (Map.Entry<String, StringBuilder> entry : buffers.entrySet()) {
                                aggregated.append(entry.getKey()).append("|").append(entry.getValue()).append(";");
                            }
                            callback.onNext(aggregated.toString(), false);
                        }
                        callback.onNext("", true);
                        callback.onComplete();
                        return;
                    }
                    try {
                        JsonNode root = MAPPER.readTree(data);
                        JsonNode choices = root.get("choices");
                        if (choices == null || choices.isEmpty()) return;
                        JsonNode delta = choices.get(0).get("delta");
                        if (delta == null) return;
                        if (delta.has("content") && !delta.get("content").isNull()) {
                            callback.onNext(delta.get("content").asText(), false);
                        }
                        if (delta.has("tool_calls")) {
                            for (JsonNode tc : delta.get("tool_calls")) {
                                int index = tc.has("index") ? tc.get("index").asInt() : 0;
                                String key = "tc_" + index;
                                if (!toolCallBuffers.get().containsKey(key)) {
                                    toolCallBuffers.get().put(key, new StringBuilder());
                                }
                                StringBuilder buf = toolCallBuffers.get().get(key);
                                if (tc.has("function")) {
                                    JsonNode func = tc.get("function");
                                    if (func.has("name") && !func.get("name").isNull()) {
                                        buf.append("name=").append(func.get("name").asText()).append("|");
                                    }
                                    if (func.has("arguments") && !func.get("arguments").isNull()) {
                                        buf.append(func.get("arguments").asText());
                                    }
                                }
                            }
                        }
                        JsonNode choice = choices.get(0);
                        if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
                            finishReason.set(choice.get("finish_reason").asText());
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse SSE event", e);
                        callback.onError(e);
                    }
                }
                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    log.error("SSE connection failed: {}", t.getMessage());
                    if (keyExhausted.get()) return;
                    if (response != null && response.code() == 429 && (attempt + 1) < maxRetries) {
                        KEY_INDEX.incrementAndGet();
                        keyExhausted.set(true);
                        log.warn("Stream 429 on attempt " + (attempt + 1) + ", retrying");
                        chatStream(messages, tools, callback);
                        return;
                    }
                    if (finishReason.get() == null) callback.onError(t);
                    else callback.onComplete();
                }
                @Override
                public void onClosed(EventSource eventSource) { log.debug("SSE connection closed"); }
            };
            EventSources.createFactory(sseHttpClient).newEventSource(request, listener);
        } catch (Exception e) {
            log.error("Failed to start streaming", e);
            callback.onError(e);
        }
    }

    @Override
    public String modelName() { return modelNameInput; }

    private ObjectNode buildRequestBody(List<Message> messages, List<String> tools, boolean stream) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", CURRENT_MODEL);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.put("stream", stream);
        ArrayNode msgArray = MAPPER.createArrayNode();
        for (Message msg : messages) {
            ObjectNode msgNode = MAPPER.createObjectNode();
            msgNode.put("role", mapRole(msg.getRole()));
            msgNode.put("content", msg.getContent() != null ? msg.getContent() : "");
            if ("assistant".equals(msg.getRole()) && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                ArrayNode calls = MAPPER.createArrayNode();
                for (ToolCall tc : msg.getToolCalls()) {
                    ObjectNode callNode = MAPPER.createObjectNode();
                    callNode.put("id", tc.getId());
                    callNode.put("type", "function");
                    ObjectNode func = callNode.putObject("function");
                    func.put("name", tc.getName());
                    try { func.put("arguments", MAPPER.writeValueAsString(tc.getArguments())); } catch (Exception e) { func.put("arguments", "{}"); }
                    calls.add(callNode);
                }
                msgNode.set("tool_calls", calls);
            }
            if ("tool".equals(msg.getRole()) && msg.getToolCallId() != null) {
                msgNode.put("tool_call_id", msg.getToolCallId());
            }
            msgArray.add(msgNode);
        }
        body.set("messages", msgArray);
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = MAPPER.createArrayNode();
            for (String def : tools) {
                try { toolsArray.add(MAPPER.readTree(def)); } catch (Exception e) { log.warn("Skipping invalid tool def: {}", def, e); }
            }
            body.set("tools", toolsArray);
        }
        return body;
    }

    private Message parseResponse(String responseBody) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode choice = root.get("choices").get(0);
            JsonNode msgNode = choice.get("message");
            String role = msgNode.get("role").asText();
            String content = msgNode.has("content") && !msgNode.get("content").isNull() ? msgNode.get("content").asText() : "";
            List<ToolCall> toolCalls = null;
            if (msgNode.has("tool_calls") && msgNode.get("tool_calls").isArray()) {
                toolCalls = new ArrayList<>();
                for (JsonNode tc : msgNode.get("tool_calls")) {
                    toolCalls.add(ToolCall.builder().id(tc.get("id").asText()).name(tc.get("function").get("name").asText()).arguments(parseArgs(tc.get("function").get("arguments").asText())).build());
                }
            }
            return Message.builder().role(role).content(content).toolCalls(toolCalls).build();
        } catch (Exception e) {
            log.error("Failed to parse LLM response", e);
            return Message.builder().role("assistant").content("[Parse Error: " + e.getMessage() + "]").build();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String argsStr) {
        try { return MAPPER.readValue(argsStr, Map.class); } catch (Exception e) { return Collections.emptyMap(); }
    }

    private String mapRole(String role) {
        if ("user".equals(role)) return "user";
        if ("tool".equals(role)) return "tool";
        return "assistant";
    }
}
