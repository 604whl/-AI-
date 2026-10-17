package com.shortvideoscripagent.xhsagentyunying.infra.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * 通过文件魔数（文件头字节）识别真实 MIME 类型，防止扩展名伪装。
 * <p>
 * 仅读取前 16 字节，不读取完整文件，性能友好。
 * </p>
 */
@Slf4j
@Component
public class MimeSniffer {

    private static final int HEADER_SIZE = 16;

    private static byte b(int v) {
        return (byte) v;
    }

    // Magic patterns: each entry is [mimeType, headerBytes, maskBytes (null = no mask)]
    @SuppressWarnings("unchecked")
    private static final List<MagicPattern> PATTERNS = List.of(
            // JPEG: FF D8 FF ..
            new MagicPattern("image/jpeg", new byte[]{b(0xFF), b(0xD8), b(0xFF)}, null),
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            new MagicPattern("image/png", new byte[]{b(0x89), b(0x50), b(0x4E), b(0x47), b(0x0D), b(0x0A), b(0x1A), b(0x0A)}, null),
            // GIF87a
            new MagicPattern("image/gif", "GIF87a".getBytes(), null),
            // GIF89a
            new MagicPattern("image/gif", "GIF89a".getBytes(), null),
            // WebP: 52 49 46 46 ?? ?? ?? ?? 57 45 42 50 (RIFF....WEBP)
            new MagicPattern("image/webp",
                    new byte[]{b(0x52), b(0x49), b(0x46), b(0x46), b(0x00), b(0x00), b(0x00), b(0x00), b(0x57), b(0x45), b(0x42), b(0x50)},
                    new byte[]{b(0x00), b(0x00), b(0x00), b(0x00)}),
            // MP4: 00 00 00 ?? 66 74 79 70
            new MagicPattern("video/mp4",
                    new byte[]{b(0x00), b(0x00), b(0x00), b(0x18), b(0x66), b(0x74), b(0x79), b(0x70)},
                    null),
            // MOV: 00 00 00 ?? 6D 6F 6F 76
            new MagicPattern("video/quicktime",
                    new byte[]{b(0x00), b(0x00), b(0x00), b(0x14), b(0x6D), b(0x6F), b(0x6F), b(0x76)},
                    null),
            // PDF: 25 50 44 46 2D (%PDF-)
            new MagicPattern("application/pdf", "%PDF-".getBytes(), null),
            // EXE / PE: 4D 5A (MZ)
            new MagicPattern("application/x-msdownload", new byte[]{b(0x4D), b(0x5A)}, null)
    );

    private static class MagicPattern {
        final String mime;
        final byte[] pattern;
        final byte[] mask; // null = no mask

        MagicPattern(String mime, byte[] pattern, byte[] mask) {
            this.mime = mime;
            this.pattern = pattern;
            this.mask = mask;
        }
    }

    /**
     * 根据文件头字节识别真实 MIME 类型。
     *
     * @param bytes 文件前至少 16 字节（不足则用实际长度）
     * @return 识别的 MIME 类型，若未知则返回 null
     */
    public String sniff(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        int len = Math.min(bytes.length, HEADER_SIZE);
        for (MagicPattern p : PATTERNS) {
            if (matches(p, bytes, len)) {
                return p.mime;
            }
        }
        return null;
    }

    /**
     * 从 InputStream 读取头部字节后识别 MIME 类型（流不会被消费完）。
     *
     * @param in 必须支持 mark/reset，否则会失败返回 null
     * @return 识别的 MIME 类型，若未知则返回 null
     */
    public String sniff(InputStream in) {
        if (!in.markSupported()) {
            log.warn("InputStream does not support mark/reset, cannot sniff MIME");
            return null;
        }
        try {
            in.mark(HEADER_SIZE);
            byte[] header = in.readNBytes(HEADER_SIZE);
            in.reset();
            return sniff(header);
        } catch (IOException e) {
            log.warn("Failed to read file header: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据扩展名返回期望的 MIME 类型，用于与魔数结果交叉比对。
     */
    public Optional<String> expectedFromExtension(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        String lower = filename.toLowerCase();
        String mime;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            mime = "image/jpeg";
        } else if (lower.endsWith(".png")) {
            mime = "image/png";
        } else if (lower.endsWith(".gif")) {
            mime = "image/gif";
        } else if (lower.endsWith(".webp")) {
            mime = "image/webp";
        } else if (lower.endsWith(".mp4")) {
            mime = "video/mp4";
        } else if (lower.endsWith(".mov")) {
            mime = "video/quicktime";
        } else {
            mime = null;
        }
        return Optional.ofNullable(mime);
    }

    /**
     * 交叉验证扩展名与魔数是否一致。
     *
     * @return true 表示一致，false 表示不一致或无法判断
     */
    public boolean matchesExtension(String filename, String detectedMime) {
        Optional<String> expected = expectedFromExtension(filename);
        if (expected.isEmpty() || detectedMime == null) {
            return true; // 无法验证时放行，由 FileValidator 其他规则兜底
        }
        return expected.get().equalsIgnoreCase(detectedMime);
    }

    private boolean matches(MagicPattern p, byte[] data, int len) {
        if (p.pattern.length > len) {
            return false;
        }
        for (int i = 0; i < p.pattern.length; i++) {
            byte expected = p.pattern[i];
            if (p.mask != null && i < p.mask.length && p.mask[i] != 0) {
                // masked byte: only compare non-mask bits
                if ((data[i] & p.mask[i]) != (expected & p.mask[i])) {
                    return false;
                }
            } else {
                if (data[i] != expected) {
                    return false;
                }
            }
        }
        return true;
    }
}
