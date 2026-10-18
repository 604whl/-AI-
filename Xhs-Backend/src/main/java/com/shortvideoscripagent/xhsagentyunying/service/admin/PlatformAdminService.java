package com.shortvideoscripagent.xhsagentyunying.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.PlatformAdmin;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.PlatformAdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private static final int CODE_FORBIDDEN = 40302;
    private final PlatformAdminMapper platformAdminMapper;

    public boolean isPlatformAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        LambdaQueryWrapper<PlatformAdmin> q = new LambdaQueryWrapper<>();
        q.eq(PlatformAdmin::getUserId, userId)
         .eq(PlatformAdmin::getEnabled, true)
         .isNull(PlatformAdmin::getRevokedAt);
        return platformAdminMapper.exists(q);
    }

    public void requirePlatformAdmin() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_FORBIDDEN, "unauthorized");
        }
        if (!isPlatformAdmin(userId)) {
            throw new BusinessException(CODE_FORBIDDEN, "platform_admin_only");
        }
    }
}
