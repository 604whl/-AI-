package com.shortvideoscripagent.xhsagentyunying.infra.upload;
public interface VirusScanner {
    String scan(byte[] data, String name) throws VirusFoundException;
}