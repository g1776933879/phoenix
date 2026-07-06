package com.your.agent.core.cron;

import com.your.agent.core.loop.ReActEngine;
import com.your.agent.core.model.AgentResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cron 定时调度器 —— 参考 Hermes cron/ 模块。
 * 支持自然语言定时任务，如"每天早上9点生成报告"。
 * 任务执行结果可投递到任意渠道。
 */
@Slf4j
public class CronScheduler {

    private final ReActEngine reActEngine;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final Map<String, CronJob> jobs = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);
    private volatile boolean running = false;

    public CronScheduler(ReActEngine reActEngine) {
        this.reActEngine = reActEngine;
        log.info("CronScheduler initialized");
    }

    /**
     * 添加定时任务
     * @param name 任务名称
     * @param prompt 任务提示词
     * @param cronExpr cron表达式 (秒 分 时 日 月 周)
     * @param channel 投递渠道
     * @return 任务ID
     */
    public String addJob(String name, String prompt, String cronExpr, String channel) {
        String id = "cron_" + idCounter.incrementAndGet();
        CronJob job = new CronJob(id, name, prompt, cronExpr, channel, CronJobStatus.ACTIVE);
        jobs.put(id, job);
        scheduleJob(job);
        log.info("Cron job added: {} ({}), expr={}", name, id, cronExpr);
        return id;
    }

    /**
     * 添加自然语言定时任务
     */
    public String addNaturalJob(String name, String prompt, String naturalTime, String channel) {
        String cronExpr = parseNaturalTime(naturalTime);
        return addJob(name, prompt, cronExpr, channel);
    }

    private void scheduleJob(CronJob job) {
        long delay = calculateInitialDelay(job.cronExpr());
        if (delay < 0) {
            log.warn("Invalid cron expression: {}", job.cronExpr());
            return;
        }

        executor.scheduleAtFixedRate(() -> executeJob(job), delay, 86400, TimeUnit.SECONDS);
    }

    private void executeJob(CronJob job) {
        if (!CronJobStatus.ACTIVE.equals(job.status())) return;

        log.info("Executing cron job: {} ({})", job.name(), job.id());
        try {
            AgentResponse response = reActEngine.run(job.prompt());
            String result = response.getFinalAnswer();
            log.info("Cron job {} completed: {}", job.id(), result.substring(0, Math.min(100, result.length())));

            // 记录执行历史
            job.addHistory(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), result);

        } catch (Exception e) {
            log.error("Cron job {} failed", job.id(), e);
            job.addHistory(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), "[Error] " + e.getMessage());
        }
    }

    public boolean pauseJob(String id) {
        CronJob job = jobs.get(id);
        if (job != null) {
            jobs.put(id, job.withStatus(CronJobStatus.PAUSED));
            return true;
        }
        return false;
    }

    public boolean resumeJob(String id) {
        CronJob job = jobs.get(id);
        if (job != null) {
            jobs.put(id, job.withStatus(CronJobStatus.ACTIVE));
            return true;
        }
        return false;
    }

    public boolean deleteJob(String id) {
        return jobs.remove(id) != null;
    }

    public List<CronJob> listJobs() {
        return new ArrayList<>(jobs.values());
    }

    public CronJob getJob(String id) {
        return jobs.get(id);
    }

    private long calculateInitialDelay(String cronExpr) {
        // 简化实现：解析 秒 分 时 日 月 周
        // 只取小时和分钟来算延迟
        try {
            String[] parts = cronExpr.split("\\s+");
            if (parts.length < 3) return -1;

            int minute = Integer.parseInt(parts[1]);
            int hour = Integer.parseInt(parts[2]);

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime next = now.withHour(hour).withMinute(minute).withSecond(0);

            if (next.isBefore(now) || next.isEqual(now)) {
                next = next.plusDays(1);
            }

            return java.time.Duration.between(now, next).getSeconds();
        } catch (Exception e) {
            log.warn("Failed to parse cron: {}", cronExpr, e);
            return 86400; // 默认24小时后
        }
    }

    private String parseNaturalTime(String natural) {
        if (natural.contains("每天") || natural.contains("每日")) {
            if (natural.contains("早上") || natural.contains("早晨")) {
                int hour = 8;
                if (natural.matches(".*\\d+点.*")) {
                    var m = java.util.regex.Pattern.compile("(\\d+)点").matcher(natural);
                    if (m.find()) hour = Integer.parseInt(m.group(1));
                }
                return String.format("0 0 %d * * *", hour);
            }
            if (natural.contains("晚上")) return "0 0 20 * * *";
            return "0 0 9 * * *"; // 默认每天9点
        }
        if (natural.contains("每") && natural.contains("小时")) {
            int interval = 1;
            var m = java.util.regex.Pattern.compile("每(\\d+)小时").matcher(natural);
            if (m.find()) interval = Integer.parseInt(m.group(1));
            return String.format("0 0 */%d * * *", interval);
        }
        // 默认每小时
        return "0 0 * * * *";
    }

    public void shutdown() {
        running = false;
        executor.shutdown();
        log.info("CronScheduler shutdown");
    }

    public enum CronJobStatus { ACTIVE, PAUSED, COMPLETED, FAILED }

    public record CronJob(String id, String name, String prompt, String cronExpr,
                          String channel, CronJobStatus status,
                          List<String> history) {
        public CronJob {
            if (history == null) history = new ArrayList<>();
        }

        public CronJob(String id, String name, String prompt, String cronExpr,
                       String channel, CronJobStatus status) {
            this(id, name, prompt, cronExpr, channel, status, new ArrayList<>());
        }

        public CronJob withStatus(CronJobStatus newStatus) {
            return new CronJob(id, name, prompt, cronExpr, channel, newStatus, history);
        }

        public void addHistory(String time, String result) {
            history.add(time + " - " + (result.length() > 200 ? result.substring(0, 200) + "..." : result));
        }
    }
}