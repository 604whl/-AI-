package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.config.AppAiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final AppAiProperties appAiProperties;

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        return ApiResponse.ok(Map.of(
                "architectureVersion", "arch-1.0.0",
                "defaultModelProvider", appAiProperties.getAi().getDefaultProvider(),
                "openaiEnabled", appAiProperties.getAi().getOpenai().isEnabled(),
                "ragEnabled", appAiProperties.getRag().isEnabled()
        ));
    }
}
