package com.shortvideoscripagent.xhsagentyunying.quota;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;

/**
 * 已冲正异常。
 */
public class AlreadyRefundedException extends BusinessException {

    private static final int CODE = 40903;

    public AlreadyRefundedException() {
        super(CODE, "already_refunded");
    }
}
