package com.shortvideoscripagent.xhsagentyunying.ai.agent.handler;

import com.shortvideoscripagent.xhsagentyunying.service.ExportService;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * P0-5: 导出任务处理器。
 * <p>
 * 注册到 {@link com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.TaskRegistry}，
 * 处理 Redis Streams 中的 "EXPORT" 类型任务。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExportTaskHandler implements TaskHandler {

    private final ExportService exportService;

    @Override
    public String taskType() {
        return "EXPORT";
    }

    @Override
    public void handle(String taskId, Map<String, Object> payload) throws Exception {
        log.info("Processing EXPORT task: {}", taskId);

        String exportIdStr = (String) payload.get("exportId");
        if (exportIdStr == null || exportIdStr.isBlank()) {
            throw new IllegalArgumentException("exportId is required in payload");
        }

        UUID exportId;
        try {
            exportId = UUID.fromString(exportIdStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid exportId: " + exportIdStr);
        }

        exportService.processExport(exportId);

        log.info("EXPORT task {} completed successfully", taskId);
    }
}
