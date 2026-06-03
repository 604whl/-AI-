package com.shortvideoscripagent.xhsagentyunying.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisStreamHub {

    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, AnalysisProgressEvent> latestByTask = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<SseEmitter>> emittersByTask = new ConcurrentHashMap<>();

    public void publish(AnalysisProgressEvent event) {
        if (event == null || event.taskId() == null) {
            return;
        }
        latestByTask.put(event.taskId(), event);
        Set<SseEmitter> emitters = emittersByTask.get(event.taskId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(emitter, event);
        }
        if (isTerminal(event.status())) {
            emitters.clear();
            emittersByTask.remove(event.taskId(), emitters);
        }
    }

    public SseEmitter subscribe(String taskId, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        emittersByTask.computeIfAbsent(taskId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);

        AnalysisProgressEvent latest = latestByTask.get(taskId);
        if (latest != null) {
            send(emitter, latest);
            if (isTerminal(latest.status())) {
                complete(emitter);
            }
        }

        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> removeEmitter(taskId, emitter));
        emitter.onError(ex -> removeEmitter(taskId, emitter));
        return emitter;
    }

    private void removeEmitter(String taskId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByTask.get(taskId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByTask.remove(taskId, emitters);
            }
        }
        complete(emitter);
    }

    private void send(SseEmitter emitter, AnalysisProgressEvent event) {
        try {
            String eventName = isTerminal(event.status()) ? "done" : "progress";
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(objectMapper.writeValueAsString(event)));
            if (isTerminal(event.status())) {
                complete(emitter);
            }
        } catch (IOException ex) {
            log.debug("Analysis SSE client disconnected for task {}", event.taskId());
            removeEmitter(event.taskId(), emitter);
        }
    }

    private static void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private static boolean isTerminal(String status) {
        return "completed".equals(status) || "failed".equals(status);
    }
}
