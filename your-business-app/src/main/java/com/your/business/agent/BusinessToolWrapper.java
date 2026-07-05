package com.your.business.agent;

import com.your.agent.core.tool.Tool;
import com.your.business.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 业务工具包装器 —— 将你现有@Service中的方法包装为Agent可调用的@Tool。
 * <p>
 * 示例：将ReportService的报表生成能力暴露给Agent。
 * 你只需把自己的Service @Autowired 进来，然后用@Tool注解标记方法。
 */
@Slf4j
@Component
public class BusinessToolWrapper {

    @Autowired(required = false)
    private ReportService reportService;

    /**
     * 生成销售报表 —— 演示如何将现有Service包装为Agent工具
     */
    @Tool(
        name = "generate_report",
        description = "根据日期范围生成销售报告。" +
                      "返回格式化的销售数据摘要，包括总销售额、订单数、同比变化等关键指标。",
        parametersSchema = "{\"type\":\"object\",\"properties\":{" +
                "\"startDate\":{\"type\":\"string\",\"description\":\"开始日期，格式YYYY-MM-DD\"}," +
                "\"endDate\":{\"type\":\"string\",\"description\":\"结束日期，格式YYYY-MM-DD\"}" +
                "},\"required\":[\"startDate\",\"endDate\"]}",
        requireApproval = true
    )
    public String generateReport(String argsJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(argsJson);
            String startDate = node.get("startDate").asText();
            String endDate = node.get("endDate").asText();

            log.info("Generating report: startDate={}, endDate={}", startDate, endDate);

            if (reportService != null) {
                return reportService.generate(startDate, endDate);
            }

            // 演示模式下返回模拟数据
            return String.format("""
                    ===== 销售报告 (%s ~ %s) =====
                    总销售额: ¥1,234,567.89
                    订单总数: 1,026 单
                    同比变化: +12.3%
                    客单价: ¥1,203.28
                    热销品类: 电子产品 / 家居用品 / 服装
                    ==============================
                    """, startDate, endDate);

        } catch (Exception e) {
            log.error("Report generation failed", e);
            return "[Error] " + e.getMessage();
        }
    }
}