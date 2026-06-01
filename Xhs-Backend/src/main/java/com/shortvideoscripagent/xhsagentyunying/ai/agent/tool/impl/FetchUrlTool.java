package com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.impl;

import com.shortvideoscripagent.xhsagentyunying.ai.agent.AgentCard;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.AgentTool;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolContext;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.tool.ToolResult;
import com.shortvideoscripagent.xhsagentyunying.ai.agent.web.UrlFetchService;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FetchUrlTool implements AgentTool {

    private final UrlFetchService urlFetchService;

    @Override
    public String name() {
        return "fetch_url";
    }

    @Override
    public String description() {
        return "抓取指定 URL 的正文内容，用于竞品链接或新闻页面解析";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "url", Map.of("type", "string", "description", "http 或 https 链接")
                ),
                "required", List.of("url")
        );
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        String url = SearchKbTool.stringArg(arguments, "url");
        if (url.isBlank()) {
            return ToolResult.fail("url_required");
        }

        try {
            UrlFetchService.FetchResult fetched = urlFetchService.fetch(url);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("url", fetched.url());
            payload.put("text", fetched.text());
            payload.put("contentType", fetched.contentType());

            AgentCard card = AgentCard.builder()
                    .type("fetched_url")
                    .payload(payload)
                    .build();

            return ToolResult.ok(payload, List.of(card));
        } catch (BusinessException ex) {
            return ToolResult.fail(ex.getMessage());
        }
    }
}
