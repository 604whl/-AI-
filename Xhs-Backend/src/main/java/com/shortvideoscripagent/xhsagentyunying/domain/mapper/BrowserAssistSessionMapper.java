package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.BrowserAssistSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface BrowserAssistSessionMapper extends BaseMapper<BrowserAssistSession> {

    @Select("SELECT * FROM app.browser_assist_session WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT 50")
    List<BrowserAssistSession> selectRecentByUser(@Param("userId") Long userId);

    @Select("SELECT * FROM app.browser_assist_session WHERE workspace_id = #{workspaceId} ORDER BY created_at DESC LIMIT 50")
    List<BrowserAssistSession> selectRecentByWorkspace(@Param("workspaceId") UUID workspaceId);

    @Select("SELECT * FROM app.browser_assist_session WHERE id = #{id} AND workspace_id = #{workspaceId}")
    BrowserAssistSession selectByIdAndWorkspace(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);
}