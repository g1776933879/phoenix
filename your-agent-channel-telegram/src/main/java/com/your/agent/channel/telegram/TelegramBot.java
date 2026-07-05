package com.your.agent.channel.telegram;

import com.your.agent.core.loop.ReActEngine;
import com.your.agent.core.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final ReActEngine reActEngine;
    private final TelegramConfig config;
    private static final List<String> ALLOWED_USERS = List.of(); // 空=允许所有人

    public TelegramBot(ReActEngine reActEngine, TelegramConfig config) {
        super(config.getBotToken());
        this.reActEngine = reActEngine;
        this.config = config;
        log.info("TelegramBot initialized: botUsername={}", config.getBotUsername());
    }

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String text = update.getMessage().getText().trim();
        long chatId = update.getMessage().getChatId();
        String userName = update.getMessage().getFrom().getUserName();
        String firstName = update.getMessage().getFrom().getFirstName();

        log.info("📩 Telegram msg from @{} ({}): {}", userName, firstName, text);

        // 处理命令
        if (text.startsWith("/")) {
            handleCommand(text, chatId);
            return;
        }

        // 异步处理，不阻塞Telegram API
        CompletableFuture.runAsync(() -> processMessage(text, chatId));
    }

    private void handleCommand(String text, long chatId) {
        switch (text.toLowerCase()) {
            case "/start" -> sendMsg(chatId, """
                    ⚡ 凤凰 Telegram Bot 已连接！
                    
                    直接发消息跟我聊天，支持：
                    💬 多轮对话（自动记忆上下文）
                    🔧 调用工具（文件/网络/命令）
                    🧠 持久记忆（L4长程记忆）
                    🧬 自我进化
                    
                    /reset — 重置对话
                    /help  — 帮助
                    /stats — 系统状态
                    """);
            case "/reset" -> {
                reActEngine.resetConversation();
                sendMsg(chatId, "🔄 对话已重置");
            }
            case "/help" -> sendMsg(chatId, """
                    /start — 开始使用
                    /reset — 重置对话上下文
                    /stats — 查看系统状态
                    /model — 查看当前模型
                    """);
            case "/stats" -> {
                int historySize = reActEngine.getConversationHistory().size();
                sendMsg(chatId, String.format("""
                        🤖 凤凰 系统状态
                        ─────────────────
                        模型: deepseek-v4-flash
                        记忆层: L1-L4 全部在线
                        上下文: %d 条消息
                        引擎: ReAct v2.0
                        """, historySize));
            }
            default -> sendMsg(chatId, "未知命令，发送 /help 查看可用命令");
        }
    }

    private void processMessage(String text, long chatId) {
        try {
            var response = reActEngine.run(text);
            String answer = response.getFinalAnswer();
            if (answer == null || answer.isEmpty()) {
                answer = "(空回复)";
            }
            sendMsg(chatId, answer);
            log.info("✅ Telegram reply sent to chatId={}, iterations={}, duration={}ms",
                    chatId, response.getIterations(), response.getDurationMs());
        } catch (Exception e) {
            log.error("Telegram message processing failed", e);
            sendMsg(chatId, "❌ 处理消息时出错: " + e.getMessage());
        }
    }

    private void sendMsg(long chatId, String text) {
        try {
            SendMessage msg = new SendMessage();
            msg.setChatId(String.valueOf(chatId));
            msg.setText(text);
            msg.enableMarkdown(true);
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message", e);
        }
    }
}