package com.your.channel.wecom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.your.agent.core.loop.ReActEngine;
import com.your.agent.core.model.AgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/channel/wecom")
public class WeComWebhook {

    private final ReActEngine reActEngine;
    private final WeComConfig config;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public WeComWebhook(ReActEngine reActEngine, WeComConfig config) {
        this.reActEngine = reActEngine;
        this.config = config;
        log.info("WeComWebhook initialized at /api/channel/wecom");
    }

    // 企业微信验证URL的GET请求
    @GetMapping
    public String verifyUrl(
            @RequestParam("msg_signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr) {
        String computed = sha1(Arrays.asList(config.getToken(), timestamp, nonce, echostr));
        if (computed.equals(signature)) {
            log.info("WeCom URL verification passed");
            return echostr;
        }
        log.warn("WeCom URL verification failed");
        return "invalid";
    }

    // 接收企业微信消息的POST请求
    @PostMapping(produces = "application/xml;charset=utf-8")
    public String handleMessage(
            @RequestParam("msg_signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestBody String xmlBody) throws Exception {

        log.info("WeCom message received: body={}", xmlBody.length() > 200 ? xmlBody.substring(0, 200) + "..." : xmlBody);

        // 简单XML解析提取文本内容
        String content = extractXmlValue(xmlBody, "Content");
        String fromUser = extractXmlValue(xmlBody, "FromUserName");
        String msgType = extractXmlValue(xmlBody, "MsgType");

        if (!"text".equals(msgType) || content == null) {
            return buildSuccessXml();
        }

        // 异步处理消息，不阻塞回调
        new Thread(() -> {
            try {
                AgentResponse response = reActEngine.run(content);
                String reply = response.getFinalAnswer();
                log.info("WeCom reply to {}: {}", fromUser, reply);
            } catch (Exception e) {
                log.error("WeCom process error", e);
            }
        }).start();

        return buildSuccessXml();
    }

    private String extractXmlValue(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int start = xml.indexOf(open);
        int end = xml.indexOf(close);
        if (start < 0 || end < 0) return null;
        return xml.substring(start + open.length(), end);
    }

    private String buildSuccessXml() {
        return "<xml><ToUserName><![CDATA[ok]]></ToUserName><FromUserName><![CDATA[ok]]></FromUserName><CreateTime>"
                + (System.currentTimeMillis() / 1000)
                + "</CreateTime><MsgType><![CDATA[text]]></MsgType><Content><![CDATA[ok]]></Content></xml>";
    }

    private static String sha1(java.util.List<String> strs) {
        try {
            StringBuilder sb = new StringBuilder();
            for (String s : strs) sb.append(s);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }
}