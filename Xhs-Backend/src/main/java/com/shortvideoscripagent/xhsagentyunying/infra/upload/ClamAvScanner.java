package com.shortvideoscripagent.xhsagentyunying.infra.upload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.upload.virus-scan.enabled", havingValue = "true", matchIfMissing = false)
public class ClamAvScanner implements VirusScanner {

    private static final int CHUNK_SIZE = 2048;

    private final VirusScanProperties properties;

    @Override
    public String scan(byte[] data, String name) throws VirusFoundException {
        if (data == null || data.length == 0) {
            return null;
        }
        try (Socket socket = new Socket(properties.getHost(), properties.getPort())) {
            socket.setSoTimeout(properties.getTimeoutMs());
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            out.write("INSTREAM".getBytes());
            out.flush();

            int offset = 0;
            while (offset < data.length) {
                int len = Math.min(CHUNK_SIZE, data.length - offset);
                out.write(new byte[]{
                        (byte) ((len >>> 24) & 0xFF),
                        (byte) ((len >>> 16) & 0xFF),
                        (byte) ((len >>> 8) & 0xFF),
                        (byte) (len & 0xFF)
                });
                out.write(data, offset, len);
                offset += len;
            }
            out.write(new byte[]{0, 0, 0, 0});
            out.flush();

            byte[] response = new byte[512];
            int read = in.read(response);
            if (read > 0) {
                String result = new String(response, 0, read).trim();
                if (result.toLowerCase().contains("found")) {
                    throw new VirusFoundException("virus_detected: " + result);
                }
                log.debug("ClamAV scan result for {}: {}", name, result);
                return result;
            }
            return null;
        } catch (VirusFoundException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new VirusScanUnavailableException("ClamAV scan timed out for " + name, e);
        } catch (java.io.IOException e) {
            throw new VirusScanUnavailableException("ClamAV scan I/O error for " + name + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new VirusScanUnavailableException("ClamAV scan failed for " + name + ": " + e.getMessage(), e);
        }
    }
}
