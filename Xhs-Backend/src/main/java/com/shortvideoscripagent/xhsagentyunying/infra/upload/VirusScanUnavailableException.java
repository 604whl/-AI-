package com.shortvideoscripagent.xhsagentyunying.infra.upload;

/**
 * 病毒扫描服务不可用时抛出此异常（fail-closed）。
 * 调用方应将此异常转换为业务异常，阻止文件上传。
 */
public class VirusScanUnavailableException extends RuntimeException {

    public VirusScanUnavailableException(String message) {
        super(message);
    }

    public VirusScanUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
