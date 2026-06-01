package com.shortvideoscripagent.xhsagentyunying.ai.agent.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentTopicDataService {

    private final ObjectMapper objectMapper;

    private volatile List<Map<String, Object>> hotTopics;
    private volatile List<Map<String, Object>> industryCalendar;

    public List<Map<String, Object>> hotTopics() {
        if (hotTopics == null) {
            synchronized (this) {
                if (hotTopics == null) {
                    hotTopics = loadList("agent/hot-topics.json");
                }
            }
        }
        return hotTopics;
    }

    public List<Map<String, Object>> industryCalendar() {
        if (industryCalendar == null) {
            synchronized (this) {
                if (industryCalendar == null) {
                    industryCalendar = loadList("agent/industry-calendar.json");
                }
            }
        }
        return industryCalendar;
    }

    private List<Map<String, Object>> loadList(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
