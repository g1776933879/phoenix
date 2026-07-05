package com.your.agent.spring.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.your.agent.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP请求工具 —— 让Agent能够发送HTTP请求获取外部数据。
 * <p>
 * 支持 GET 和 POST 方法，可设置请求头和超时。
 * 默认超时30秒，最大响应体500KB。
 */
@Slf4j
@Component
public class HttpTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RESPONSE_SIZE = 500 * 1024; // 500KB

    private final HttpClient httpClient;

    public HttpTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Tool(
        name = "http_get",
        description = "发送HTTP GET请求获取指定URL的内容。" +
                      "适用于获取公开API数据、网页内容等。",
        parametersSchema = "{\"type\":\"object\",\"properties\":{" +
                "\"url\":{\"type\":\"string\",\"description\":\"请求URL\"}," +
                "\"headers\":{\"type\":\"object\",\"description\":\"可选的请求头，如{\\\"Authorization\\\":\\\"Bearer xxx\\\"}\"}," +
                "\"timeoutSeconds\":{\"type\":\"integer\",\"description\":\"超时秒数，默认30\"}" +
                "},\"required\":[\"url\"]}"
    )
    public String httpGet(String argsJson) {
        try {
            var node = MAPPER.readTree(argsJson);
            String url = node.get("url").asText();
            int timeout = node.has("timeoutSeconds") ? node.get("timeoutSeconds").asInt(30) : 30;

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .GET();

            // 添加自定义请求头
            if (node.has("headers") && !node.get("headers").isNull()) {
                var headers = MAPPER.convertValue(node.get("headers"), Map.class);
                headers.forEach((k, v) -> builder.header(k.toString(), v.toString()));
            }

            HttpRequest request = builder.build();
            log.info("HTTP GET: {}", url);

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            String body = response.body();
            if (body.length() > MAX_RESPONSE_SIZE) {
                body = body.substring(0, MAX_RESPONSE_SIZE) + "\n... [truncated at 500KB]";
            }

            log.info("HTTP GET completed: status={}, bodyLength={}", response.statusCode(), body.length());

            return String.format("Status: %d\nHeaders: %s\n\nBody:\n%s",
                    response.statusCode(),
                    response.headers().map(),
                    body);

        } catch (Exception e) {
            log.error("httpGet failed", e);
            return "[Error] " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    @Tool(
        name = "http_post",
        description = "发送HTTP POST请求，可用于调用API、提交数据等。" +
                      "支持JSON格式的请求体。",
        parametersSchema = "{\"type\":\"object\",\"properties\":{" +
                "\"url\":{\"type\":\"string\",\"description\":\"请求URL\"}," +
                "\"body\":{\"type\":\"object\",\"description\":\"JSON格式的请求体\"}," +
                "\"headers\":{\"type\":\"object\",\"description\":\"可选的请求头\"}," +
                "\"timeoutSeconds\":{\"type\":\"integer\",\"description\":\"超时秒数，默认30\"}" +
                "},\"required\":[\"url\",\"body\"]}",
        requireApproval = true
    )
    public String httpPost(String argsJson) {
        try {
            var node = MAPPER.readTree(argsJson);
            String url = node.get("url").asText();
            int timeout = node.has("timeoutSeconds") ? node.get("timeoutSeconds").asInt(30) : 30;

            String requestBody = node.get("body").toString();

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            // 添加自定义请求头
            if (node.has("headers") && !node.get("headers").isNull()) {
                var headers = MAPPER.convertValue(node.get("headers"), Map.class);
                headers.forEach((k, v) -> {
                    if (!"Content-Type".equalsIgnoreCase(k.toString())) {
                        builder.header(k.toString(), v.toString());
                    }
                });
            }

            HttpRequest request = builder.build();
            log.info("HTTP POST: {}, bodyLength={}", url, requestBody.length());

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();
            if (responseBody.length() > MAX_RESPONSE_SIZE) {
                responseBody = responseBody.substring(0, MAX_RESPONSE_SIZE) + "\n... [truncated at 500KB]";
            }

            log.info("HTTP POST completed: status={}, bodyLength={}",
                    response.statusCode(), responseBody.length());

            return String.format("Status: %d\n\nResponse:\n%s",
                    response.statusCode(), responseBody);

        } catch (Exception e) {
            log.error("httpPost failed", e);
            return "[Error] " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}