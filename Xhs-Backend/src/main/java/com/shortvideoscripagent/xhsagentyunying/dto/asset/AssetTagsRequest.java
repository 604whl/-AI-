package com.shortvideoscripagent.xhsagentyunying.dto.asset;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class AssetTagsRequest {

    @Size(max = 20, message = "max_20_tags")
    private List<@Size(max = 20, message = "tag_max_20_chars") String> tags;

    @Size(max = 20, message = "max_20_tags")
    private List<@Size(max = 20, message = "tag_max_20_chars") String> addTags;

    @Size(max = 20, message = "max_20_tags")
    private List<@Size(max = 20, message = "tag_max_20_chars") String> removeTags;
}
