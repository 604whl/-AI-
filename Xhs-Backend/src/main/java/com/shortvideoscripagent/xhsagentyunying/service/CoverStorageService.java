package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppStorageProperties;
import com.shortvideoscripagent.xhsagentyunying.dto.file.CoverUploadResponse;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverStorageService {

    private static final int CODE_COVER_INVALID = 40004;
    private static final int CODE_STORAGE_ERROR = 50004;
    private static final int CODE_STORAGE_UNSUPPORTED = 50005;
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final AppStorageProperties storageProperties;

    public CoverUploadResponse upload(Long userId, MultipartFile file) {
        validateFile(file);
        String ext = resolveExtension(file);
        String objectKey = "covers/" + userId + "/" + UUID.randomUUID().toString().replace("-", "") + ext;

        if ("minio".equalsIgnoreCase(storageProperties.getType())) {
            try {
                uploadToMinio(objectKey, file);
                String url = storageProperties.getLocal().getPublicBaseUrl() + "/" + objectKey;
                return new CoverUploadResponse(url, objectKey);
            } catch (Exception ex) {
                log.warn("MinIO upload failed for {}: {}", objectKey, ex.getMessage());
                throw new BusinessException(CODE_STORAGE_ERROR, "storage_error");
            }
        }
        if ("local".equalsIgnoreCase(storageProperties.getType())) {
            uploadToLocal(objectKey, file);
            String url = storageProperties.getLocal().getPublicBaseUrl() + "/" + objectKey;
            return new CoverUploadResponse(url, objectKey);
        }
        throw new BusinessException(CODE_STORAGE_UNSUPPORTED, "storage_type_unsupported");
    }

    public String extractObjectKey(String coverImageUrl) {
        if (coverImageUrl == null || coverImageUrl.isBlank()) {
            throw new BusinessException(CODE_COVER_INVALID, "cover_image_invalid");
        }
        String marker = "/files/cover/";
        int idx = coverImageUrl.indexOf(marker);
        if (idx >= 0) {
            return coverImageUrl.substring(idx + marker.length());
        }
        String trimmed = coverImageUrl.replaceFirst("^/+", "");
        if (trimmed.startsWith("covers/")) {
            return trimmed;
        }
        throw new BusinessException(CODE_COVER_INVALID, "cover_image_invalid");
    }

    public StoredObject load(String objectKey) {
        if (!isValidObjectKey(objectKey)) {
            throw new BusinessException(CODE_COVER_INVALID, "cover_image_invalid");
        }
        if ("minio".equalsIgnoreCase(storageProperties.getType())) {
            try {
                return loadFromMinio(objectKey);
            } catch (Exception ex) {
                log.debug("MinIO read failed for {}: {}", objectKey, ex.getMessage());
                throw new BusinessException(CODE_STORAGE_ERROR, "storage_error");
            }
        }
        if ("local".equalsIgnoreCase(storageProperties.getType())) {
            return loadFromLocal(objectKey);
        }
        throw new BusinessException(CODE_STORAGE_UNSUPPORTED, "storage_type_unsupported");
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(CODE_COVER_INVALID, "cover_image_invalid");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(CODE_COVER_INVALID, "cover_image_invalid");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(CODE_COVER_INVALID, "cover_image_invalid");
        }
    }

    private String resolveExtension(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            return ".jpg";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private void uploadToMinio(String objectKey, MultipartFile file) throws Exception {
        AppStorageProperties.Minio minio = storageProperties.getMinio();
        MinioClient client = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(minio.getBucket()).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(minio.getBucket()).build());
        }
        try (InputStream input = file.getInputStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(minio.getBucket())
                    .object(objectKey)
                    .stream(input, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }
    }

    private StoredObject loadFromMinio(String objectKey) throws Exception {
        AppStorageProperties.Minio minio = storageProperties.getMinio();
        MinioClient client = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
        var response = client.getObject(GetObjectArgs.builder()
                .bucket(minio.getBucket())
                .object(objectKey)
                .build());
        String contentType = response.headers().get("Content-Type");
        if (!StringUtils.hasText(contentType)) {
            contentType = guessContentType(objectKey);
        }
        return new StoredObject(response.readAllBytes(), contentType);
    }

    private void uploadToLocal(String objectKey, MultipartFile file) {
        try {
            Path target = resolveLocalPath(objectKey);
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            throw new BusinessException(CODE_STORAGE_ERROR, "storage_error");
        }
    }

    private StoredObject loadFromLocal(String objectKey) {
        try {
            Path path = resolveLocalPath(objectKey);
            if (!Files.exists(path)) {
                throw new BusinessException(CODE_COVER_INVALID, "cover_image_invalid");
            }
            byte[] bytes = Files.readAllBytes(path);
            return new StoredObject(bytes, guessContentType(objectKey));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(CODE_STORAGE_ERROR, "storage_error");
        }
    }

    private Path resolveLocalPath(String objectKey) {
        return Paths.get(storageProperties.getLocal().getBaseDir()).resolve(objectKey.replace("/", java.io.File.separator));
    }

    private boolean isValidObjectKey(String objectKey) {
        return objectKey != null
                && objectKey.startsWith("covers/")
                && !objectKey.contains("..");
    }

    private String guessContentType(String objectKey) {
        String lower = objectKey.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public record StoredObject(byte[] bytes, String contentType) {
    }
}
