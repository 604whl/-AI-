package com.shortvideoscripagent.xhsagentyunying.infra.scheduler;

import com.shortvideoscripagent.xhsagentyunying.service.HotTopicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 选题池定时任务：每 4h 调用 GetHotTopicsTool 写入 app.hot_topic。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TopicPoolScheduler {

    private final HotTopicService hotTopicService;

    @Scheduled(initialDelay = 60_000, fixedRate = 4 * 60 * 60 * 1000)
    public void refreshHotTopics() {
        log.info("[TopicPoolScheduler] 开始拉取热门话题...");
        try {
            int count = hotTopicService.refreshFromAgentTool();
            log.info("[TopicPoolScheduler] 热门话题拉取完成，写入 {} 条记录", count);
        } catch (Exception e) {
            log.error("[TopicPoolScheduler] 热门话题拉取失败: {}", e.getMessage(), e);
        }
    }
}