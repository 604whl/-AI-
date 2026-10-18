package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue;

import com.shortvideoscripagent.xhsagentyunying.config.TaskQueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueueStartupListener {

    private final TaskQueueProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("task-queue enabled={}, stream-key={}, consumer-group={}, worker-id={}",
                properties.isEnabled(), properties.getStreamKey(), properties.getConsumerGroup(), properties.getWorkerId());
    }
}
