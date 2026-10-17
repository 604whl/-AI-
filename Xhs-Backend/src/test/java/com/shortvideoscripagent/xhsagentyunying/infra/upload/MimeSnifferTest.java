package com.shortvideoscripagent.xhsagentyunying.infra.upload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MimeSniffer 单元测试：验证魔数识别正确性。
 */
class MimeSnifferTest {

    private final MimeSniffer sniffer = new MimeSniffer();

    // ---------- 有效文件类型 ----------

    @Test
    @DisplayName("JPEG 文件头识别为 image/jpeg")
    void sniff_jpeg() {
        byte[] header = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        assertEquals("image/jpeg", sniffer.sniff(header));
    }

    @Test
    @DisplayName("PNG 文件头识别为 image/png")
    void sniff_png() {
        byte[] header = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
        assertEquals("image/png", sniffer.sniff(header));
    }

    @Test
    @DisplayName("GIF87a 文件头识别为 image/gif")
    void sniff_gif87a() {
        byte[] header = "GIF87a".getBytes();
        assertEquals("image/gif", sniffer.sniff(header));
    }

    @Test
    @DisplayName("GIF89a 文件头识别为 image/gif")
    void sniff_gif89a() {
        byte[] header = "GIF89a".getBytes();
        assertEquals("image/gif", sniffer.sniff(header));
    }

    @Test
    @DisplayName("WebP RIFF 文件头识别为 image/webp")
    void sniff_webp() {
        byte[] header = new byte[]{
                (byte) 0x52, (byte) 0x49, (byte) 0x46, (byte) 0x46,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x57, (byte) 0x45, (byte) 0x42, (byte) 0x50
        };
        assertEquals("image/webp", sniffer.sniff(header));
    }

    @Test
    @DisplayName("MP4 文件头识别为 video/mp4")
    void sniff_mp4() {
        byte[] header = new byte[]{
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x18,
                (byte) 0x66, (byte) 0x74, (byte) 0x79, (byte) 0x70
        };
        assertEquals("video/mp4", sniffer.sniff(header));
    }

    @Test
    @DisplayName("PDF 文件头识别为 application/pdf")
    void sniff_pdf() {
        byte[] header = "%PDF-1.4".getBytes();
        assertEquals("application/pdf", sniffer.sniff(header));
    }

    @Test
    @DisplayName("PE (EXE) 文件头识别为 application/x-msdownload")
    void sniff_exe() {
        byte[] header = new byte[]{(byte) 0x4D, (byte) 0x5A, (byte) 0x90, (byte) 0x00};
        assertEquals("application/x-msdownload", sniffer.sniff(header));
    }

    // ---------- 伪装文件测试 ----------

    @Test
    @DisplayName("扩展名是 .jpg 但内容是 EXE 头，应识别为 exe 而非 jpeg")
    void sniff_jpg_extension_but_exe_content() {
        byte[] header = new byte[]{(byte) 0x4D, (byte) 0x5A, (byte) 0x90, (byte) 0x00};
        String detected = sniffer.sniff(header);
        assertEquals("application/x-msdownload", detected);
        assertNotEquals("image/jpeg", detected);
    }

    @Test
    @DisplayName("扩展名是 .png 但内容是 JPEG，应识别为 jpeg")
    void sniff_png_extension_but_jpeg_content() {
        byte[] header = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        String detected = sniffer.sniff(header);
        assertEquals("image/jpeg", detected);
        assertNotEquals("image/png", detected);
    }

    // ---------- 扩展名匹配测试 ----------

    @Test
    @DisplayName("filename .jpg 与 image/jpeg 匹配")
    void matchesExtension_jpg() {
        assertTrue(sniffer.matchesExtension("photo.jpg", "image/jpeg"));
        assertTrue(sniffer.matchesExtension("photo.jpeg", "image/jpeg"));
    }

    @Test
    @DisplayName("filename .jpg 与 image/png 不匹配")
    void matchesExtension_jpg_vs_png() {
        assertFalse(sniffer.matchesExtension("photo.jpg", "image/png"));
    }

    @Test
    @DisplayName("未知扩展名放行（返回 true）")
    void matchesExtension_unknown() {
        assertTrue(sniffer.matchesExtension("photo.xyz", "image/jpeg"));
    }

    @Test
    @DisplayName("未知 MIME 类型时，返回 false")
    void matchesExtension_unknown_mime() {
        assertTrue(sniffer.matchesExtension("photo.jpg", null));
        assertFalse(sniffer.matchesExtension("photo.jpg", "application/octet-stream"));
    }

    // ---------- 边界测试 ----------

    @Test
    @DisplayName("空字节数组返回 null")
    void sniff_empty() {
        assertNull(sniffer.sniff(new byte[]{}));
        assertNull(sniffer.sniff((byte[]) null));
    }

    @Test
    @DisplayName("随机字节返回 null（未知类型）")
    void sniff_unknown() {
        byte[] randomBytes = new byte[]{(byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78, (byte) 0x9A, (byte) 0xBC};
        assertNull(sniffer.sniff(randomBytes));
    }

    @Test
    @DisplayName("InputStream.readNBytes 读取后正确识别")
    void sniff_inputStream() throws IOException {
        byte[] header = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        ByteArrayInputStream bais = new ByteArrayInputStream(header);
        assertEquals("image/jpeg", sniffer.sniff(bais));
    }

    @Test
    @DisplayName("expectedFromExtension 返回正确的 MIME 类型")
    void expectedFromExtension() {
        assertEquals("image/jpeg", sniffer.expectedFromExtension("a.jpg").orElse(null));
        assertEquals("image/jpeg", sniffer.expectedFromExtension("a.jpeg").orElse(null));
        assertEquals("image/png", sniffer.expectedFromExtension("a.png").orElse(null));
        assertEquals("image/gif", sniffer.expectedFromExtension("a.gif").orElse(null));
        assertEquals("image/webp", sniffer.expectedFromExtension("a.webp").orElse(null));
        assertEquals("video/mp4", sniffer.expectedFromExtension("a.mp4").orElse(null));
        assertEquals("video/quicktime", sniffer.expectedFromExtension("a.mov").orElse(null));
        assertTrue(sniffer.expectedFromExtension("a.xyz").isEmpty());
        assertTrue(sniffer.expectedFromExtension(null).isEmpty());
    }
}
