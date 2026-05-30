package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.auth.dto.UserUsageVo;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.UsageLog;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.User;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UsageLogMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class UserQuotaService {

    public static final String ACTION_ANALYSIS = "analysis";

    private static final int CODE_QUOTA_EXCEEDED = 42901;

    private final UserMapper userMapper;
    private final UsageLogMapper usageLogMapper;

    public int remainingAnalysisQuota(Long userId) {
        return getAnalysisUsage(userId).getRemaining();
    }

    public UserUsageVo getAnalysisUsage(Long userId) {
        User user = requireUser(userId);
        int dailyQuota = user.getDailyQuota();
        int usedToday = usageLogMapper.countTodayByUserAndAction(userId, ACTION_ANALYSIS);
        int remaining = Math.max(dailyQuota - usedToday, 0);
        return UserUsageVo.builder()
                .dailyQuota(dailyQuota)
                .usedToday(usedToday)
                .remaining(remaining)
                .build();
    }

    @Transactional
    public void consumeAnalysisQuota(Long userId, String taskId) {
        if (remainingAnalysisQuota(userId) <= 0) {
            throw new BusinessException(CODE_QUOTA_EXCEEDED, "quota_exceeded");
        }
        UsageLog log = new UsageLog();
        log.setUserId(userId);
        log.setAction(ACTION_ANALYSIS);
        log.setTaskId(taskId);
        log.setCreatedAt(OffsetDateTime.now());
        usageLogMapper.insert(log);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(40401, "user not found");
        }
        if (user.getDailyQuota() == null) {
            user.setDailyQuota(3);
        }
        return user;
    }
}
