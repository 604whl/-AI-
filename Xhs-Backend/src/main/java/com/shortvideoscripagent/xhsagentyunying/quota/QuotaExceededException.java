package com.shortvideoscripagent.xhsagentyunying.quota;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;

/**
 * 配额超限异常。
 */
public class QuotaExceededException extends BusinessException {

    private static final int CODE = 42901;

    public QuotaExceededException() {
        super(CODE, "quota_exceeded");
    }

    public QuotaExceededException(String message) {
        super(CODE, message);
    }
}
