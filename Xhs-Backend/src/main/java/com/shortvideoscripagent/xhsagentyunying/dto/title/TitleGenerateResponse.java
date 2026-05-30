package com.shortvideoscripagent.xhsagentyunying.dto.title;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TitleGenerateResponse {

    private String analysisId;
    private String goal;
    private String promptVersion;
    private List<TitleItem> titles;

    @Data
    @AllArgsConstructor
    public static class TitleItem {
        private String text;
        private List<String> highlights;
        private String estimatedCtr;
    }
}
