package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ReviewComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ReviewCommentMapper extends BaseMapper<ReviewComment> {

    /**
     * 按审核任务查询所有批注（按创建时间升序）。
     */
    @Select("""
            SELECT * FROM app.review_comment
            WHERE review_task_id = #{reviewTaskId}
            ORDER BY created_at ASC
            """)
    List<ReviewComment> selectByTaskId(@Param("reviewTaskId") UUID reviewTaskId);
}
