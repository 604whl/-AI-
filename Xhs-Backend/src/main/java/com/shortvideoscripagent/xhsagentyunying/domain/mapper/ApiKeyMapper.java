package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {

    @Select("SELECT * FROM app.api_key WHERE key_hash = #{keyHash} AND revoked_at IS NULL LIMIT 1")
    Optional<ApiKey> findByHash(@Param("keyHash") String keyHash);

    @Select("SELECT * FROM app.api_key WHERE workspace_id = #{workspaceId} AND revoked_at IS NULL ORDER BY created_at DESC")
    List<ApiKey> listActiveByWorkspace(@Param("workspaceId") String workspaceId);

    @Update("UPDATE app.api_key SET last_used_at = #{now} WHERE id = #{id}")
    void touchLastUsed(@Param("id") Long id, @Param("now") OffsetDateTime now);

    @Update("UPDATE app.api_key SET revoked_at = #{now} WHERE id = #{id}")
    void revoke(@Param("id") Long id, @Param("now") OffsetDateTime now);
}
