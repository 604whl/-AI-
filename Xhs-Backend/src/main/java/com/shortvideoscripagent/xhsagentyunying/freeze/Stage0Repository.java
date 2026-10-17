package com.shortvideoscripagent.xhsagentyunying.freeze;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 阶段 0：平台 / 连接器 / 品类规则的仓储层（MyBatis-Plus 注解版）。
 * <p>
 * 这里仅暴露阶段 0 服务层需要的查询方法；写操作通过 service 层完成。
 */
@Repository
public class Stage0Repository {

    private final PlatformEntityMapper platformMapper;

    public Stage0Repository(PlatformEntityMapper platformMapper) {
        this.platformMapper = platformMapper;
    }

    public List<PlatformEntity> listAllPlatforms() {
        QueryWrapper<PlatformEntity> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort_order".equals("sort_order") ? "code" : "code");
        return platformMapper.selectList(wrapper);
    }

    public Optional<PlatformEntity> findPlatform(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(platformMapper.selectById(code));
    }
}
