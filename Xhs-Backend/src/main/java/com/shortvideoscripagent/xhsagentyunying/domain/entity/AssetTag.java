package com.shortvideoscripagent.xhsagentyunying.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("asset_tag")
public class AssetTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetId;
    private String tag;
    private OffsetDateTime createdAt;
}