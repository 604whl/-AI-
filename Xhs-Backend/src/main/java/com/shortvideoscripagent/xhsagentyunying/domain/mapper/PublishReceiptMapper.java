package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PublishReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * S3-4 发布回执 Mapper。
 * <p>
 * <b>工作区隔离是硬性要求</b>：所有查询都必须带上 {@code workspace_id = ?}。
 */
@Mapper
public interface PublishReceiptMapper extends BaseMapper<PublishReceipt> {

    /**
     * 按主键 + 工作区查询。
     */
    @Select("SELECT * FROM app.publish_receipt WHERE id = #{id} AND workspace_id = #{workspaceId}")
    PublishReceipt selectByIdAndWorkspace(@Param("id") UUID id, @Param("workspaceId") String workspaceId);

    /**
     * 按发布任务查询回执（一个任务最多一条）。
     */
    @Select("SELECT * FROM app.publish_receipt WHERE publish_task_id = #{publishTaskId}")
    PublishReceipt selectByTaskId(@Param("publishTaskId") UUID publishTaskId);

    /**
     * 列出某资产在工作区下的全部回执。
     */
    @Select("""
            SELECT * FROM app.publish_receipt
             WHERE workspace_id = #{workspaceId}
               AND asset_id = #{assetId}
             ORDER BY created_at DESC
            """)
    List<PublishReceipt> selectByAsset(@Param("workspaceId") String workspaceId,
                                       @Param("assetId") UUID assetId);

    /**
     * 列出某工作区下全部回执（按创建时间倒序）。
     */
    @Select("""
            SELECT * FROM app.publish_receipt
             WHERE workspace_id = #{workspaceId}
             ORDER BY created_at DESC
            """)
    List<PublishReceipt> selectByWorkspace(@Param("workspaceId") String workspaceId);
}
