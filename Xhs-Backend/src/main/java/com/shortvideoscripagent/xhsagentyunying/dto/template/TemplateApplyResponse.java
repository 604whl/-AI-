package com.shortvideoscripagent.xhsagentyunying.dto.template;

import com.shortvideoscripagent.xhsagentyunying.dto.asset.AssetResponse;
import lombok.Builder;
import lombok.Data;

/**
 * 模板应用响应（P0-4，M0-3 扩展可选 asset 字段）。
 */
@Data
@Builder
public class TemplateApplyResponse {

    /** 模板 ID */
    private String templateId;

    /** 渲染后的正文 */
    private String body;

    /** 更新后的引用次数 */
    private Integer useCount;

    /**
     * M0-3: 若调用时 createAsset=true，此处返回新建的草稿资产；
     * 否则为 null。
     */
    private AssetResponse asset;
}
