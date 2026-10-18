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
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileValidator {

    private static final int CODE_INVALID = 40004;

    public static final long MAX_BYTES = 5L * 1024 * 1024;
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
    private final VirusScanner virusScanner;

    public void validate(MultipartFile file) {
        validateNotEmpty(file);
        validateSize(file);
        validateMagicBytes(file);
        validateExtension(file);
        if (isImage(file.getContentType())) {
            validateImageDimensions(file);
        }
        validateVirusScan(file);
    }

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
            byte[] allBytes = file.getBytes();
            byte[] header = allBytes.length > 16 ? java.util.Arrays.copyOf(allBytes, 16) : allBytes;
            String detected = mimeSniffer.sniff(header);
            if (detected == null) {
                log.debug("Unknown magic bytes for upload: {}", file.getOriginalFilename());
                return;
            }
            if (!ALLOWED_MIME_TYPES.contains(detected)) {
                log.warn("Upload rejected: detected {} for file {}, not in allowlist",
                        detected, file.getOriginalFilename());
                throw new BusinessException(CODE_INVALID, "file_type_mismatch");
            }
            String filename = file.getOriginalFilename();
            if (filename != null && !mimeSniffer.matchesExtension(filename, detected)) {
                log.warn("Upload rejected: MIME {} mismatches extension for {}", detected, filename);
                throw new BusinessException(4002, "mime_extension_mismatch");
            }
        } catch (BusinessException e) {
            throw e;
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
        try {
            byte[] allBytes = file.getBytes();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(allBytes));
            if (img == null) {
                log.warn("Could not read image dimensions for {}", file.getOriginalFilename());
                return;
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

    private void validateVirusScan(MultipartFile file) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read file bytes for virus scan: {}", e.getMessage());
            throw new BusinessException(CODE_INVALID, "file_read_error");
        }
        try {
            virusScanner.scan(data, file.getOriginalFilename());
        } catch (VirusFoundException ex) {
            log.warn("Virus detected in {}: {}", file.getOriginalFilename(), ex.getMessage());
            throw new BusinessException(4001, "virus_detected");
        } catch (VirusScanUnavailableException ex) {
            log.error("Virus scan unavailable for {}: {}", file.getOriginalFilename(), ex.getMessage());
            throw new BusinessException(4003, "virus_scan_unavailable");
        } catch (Exception ex) {
            log.error("Unexpected virus scan error for {}: {}", file.getOriginalFilename(), ex.getMessage());
            throw new BusinessException(4003, "virus_scan_unavailable");
        }
    }
}