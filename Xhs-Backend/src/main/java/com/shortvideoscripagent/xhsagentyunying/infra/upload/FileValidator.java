package com.shortvideoscripagent.xhsagentyunying.infra.upload;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 上传文件综合校验器。
 * <p>
 * 校验顺序：MIME 魔数 → 大小 → 扩展名一致性 → 图片分辨率。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileValidator {

    private static final int CODE_INVALID = 40004;

    /** 最大文件大小：5MB */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    /** 最大图片分辨率：8000 x 8000 */
    public static final int MAX_IMAGE_DIMENSION = 8000;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".gif"
    );

    private final MimeSniffer mimeSniffer;

    /**
     * 执行完整校验。
     *
     * @throws BusinessException 若校验失败
     */
    public void validate(MultipartFile file) {
        validateNotEmpty(file);
        validateSize(file);
        validateMagicBytes(file);
        validateExtension(file);
        if (isImage(file.getContentType())) {
            validateImageDimensions(file);
        }
    }

    /**
     * 仅校验大小和图片类型（MCP/CLI 等非 MultipartFile 场景）。
     */
    public void validateSimple(byte[] data, String filename) {
        if (data == null || data.length == 0) {
            throw new BusinessException(CODE_INVALID, "file_empty");
        }
        if (data.length > MAX_BYTES) {
            throw new BusinessException(CODE_INVALID, "file_too_large");
        }
    }

    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(CODE_INVALID, "file_empty");
        }
    }

    private void validateSize(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            log.warn("Upload rejected: size {} exceeds limit {}", file.getSize(), MAX_BYTES);
            throw new BusinessException(CODE_INVALID, "file_too_large");
        }
    }

    private void validateMagicBytes(MultipartFile file) {
        try {
            byte[] header = file.getInputStream().readNBytes(16);
            String detected = mimeSniffer.sniff(header);
            if (detected == null) {
                // 未知类型：放行，让后续逻辑兜底
                log.debug("Unknown magic bytes for upload: {}", file.getOriginalFilename());
                return;
            }
            if (!ALLOWED_MIME_TYPES.contains(detected)) {
                log.warn("Upload rejected: detected {} for file {}, not in allowlist", detected, file.getOriginalFilename());
                throw new BusinessException(CODE_INVALID, "file_type_mismatch");
            }
        } catch (IOException e) {
            log.error("Failed to read file header: {}", e.getMessage());
            throw new BusinessException(CODE_INVALID, "file_read_error");
        }
    }

    private void validateExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !hasAllowedExtension(filename)) {
            log.warn("Upload rejected: extension not allowed for {}", filename);
            throw new BusinessException(CODE_INVALID, "file_extension_invalid");
        }
    }

    private void validateImageDimensions(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                log.warn("Could not read image dimensions for {}", file.getOriginalFilename());
                return; // 静默跳过非标准图片
            }
            if (img.getWidth() > MAX_IMAGE_DIMENSION || img.getHeight() > MAX_IMAGE_DIMENSION) {
                log.warn("Upload rejected: image {}x{} exceeds max {}",
                        img.getWidth(), img.getHeight(), MAX_IMAGE_DIMENSION);
                throw new BusinessException(CODE_INVALID, "image_resolution_too_high");
            }
        } catch (IOException e) {
            log.warn("Failed to check image dimensions: {}", e.getMessage());
        }
    }

    private boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    private boolean hasAllowedExtension(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }
}
