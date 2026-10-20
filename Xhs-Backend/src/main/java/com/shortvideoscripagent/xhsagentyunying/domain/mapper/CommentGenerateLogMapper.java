package com.shortvideoscripagent.xhsagentyunying.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.CommentGenerateLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论生成审计日志 Mapper。
 */
@Mapper
public interface CommentGenerateLogMapper extends BaseMapper<CommentGenerateLog> {
}