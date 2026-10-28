package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.common.api.WorkspaceContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.config.AppStorageProperties;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.MediaAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.MediaAssetMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.media.MediaUploadResponse;
import com.shortvideoscripagent.xhsagentyunying.infra.upload.FileValidator;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaStorageService {

    private static final int CODE_INVALID = 40004;
    private static final int CODE_STORAGE = 50004;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024;
    private static final int THUMBNAIL_WIDTH = 320;
    private static final int THUMBNAIL_HEIGHT = 180;

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm", "video/quicktime");

    private final AppStorageProperties storageProperties;
    private final FileValidator fileValidator;
    private final MediaAssetMapper mediaAssetMapper;

    @Transactional
    public MediaUploadResponse uploadMedia(Long userId, MultipartFile file, String assetIdStr) {
        String wsIdStr = WorkspaceContext.requireWorkspaceId();
        UUID assetId = (assetIdStr != null && !assetIdStr.isBlank()) ? UUID.fromString(assetIdStr) : null;

        String contentType = file.getContentType();
        boolean isImage = contentType != null && IMAGE_TYPES.contains(contentType.toLowerCase());
        boolean isVideo = contentType != null && VIDEO_TYPES.contains(contentType.toLowerCase());

        if (!isImage && !isVideo) {
            throw new BusinessException(CODE_INVALID, "unsupported_media_type");
        }

        fileValidator.validate(file);

        String prefix = isVideo ? "videos" : "images";
        String ext = resolveExt(contentType, file.getOriginalFilename());
        String objectKey = prefix + "/" + wsIdStr + "/" + userId + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        String thumbKey = null;

        // 写入存储
        uploadObject(objectKey, file);

        // 生成缩略图（图片直接生成，视频记录待处理）
        if (isImage) {
            thumbKey = generateThumbnail(objectKey, file);
        }

        // 解析尺寸
        int[] dims = readImageDimensions(file);

        // 入库
        MediaAsset ma = new MediaAsset();
        ma.setWorkspaceId(wsIdStr);
        ma.setUserId(userId);
        ma.setAssetId(assetId);
        ma.setMediaType(isVideo ? MediaAsset.TYPE_VIDEO : MediaAsset.TYPE_IMAGE);
        ma.setObjectKey(objectKey);
        ma.setFilename(file.getOriginalFilename());
        ma.setFileSize(file.getSize());
        ma.setContentType(contentType);
        ma.setWidth(dims[0]);
        ma.setHeight(dims[1]);
        ma.setThumbnailKey(thumbKey);
        ma.setStatus(MediaAsset.STATUS_READY);
        ma.setScanStatus(MediaAsset.SCAN_CLEAN);
        ma.setCreatedAt(OffsetDateTime.now());
        ma.setUpdatedAt(OffsetDateTime.now());
        mediaAssetMapper.insert(ma);

        log.info("Media uploaded: id={}, type={}, size={}, key={}", ma.getId(), ma.getMediaType(), file.getSize(), objectKey);

        return buildResponse(ma);
    }

    public MediaUploadResponse getMedia(Long userId, Long mediaId) {
        MediaAsset ma = mediaAssetMapper.selectById(mediaId);
        if (ma == null) throw new BusinessException(40460, "media_not_found");
        if (!ma.getWorkspaceId().toString().equals(WorkspaceContext.requireWorkspaceId())) {
            throw new BusinessException(40301, "forbidden");
        }
        return buildResponse(ma);
    }

    public List<MediaUploadResponse> listMedia(Long userId) {
        String wsIdStr = WorkspaceContext.requireWorkspaceId();
        return mediaAssetMapper.selectRecentByWorkspace(wsIdStr).stream()
                .map(this::buildResponse)
                .toList();
    }

    public List<MediaUploadResponse> listByAsset(Long userId, UUID assetId) {
        return mediaAssetMapper.selectByAssetId(assetId).stream()
                .map(this::buildResponse)
                .toList();
    }

    private void uploadObject(String objectKey, MultipartFile file) {
        try {
            if ("minio".equalsIgnoreCase(storageProperties.getType())) {
                uploadToMinio(objectKey, file);
            } else {
                uploadToLocal(objectKey, file);
            }
        } catch (BusinessException e) { throw e; }
        catch (Exception e) {
            log.error("upload failed: key={} error={}", objectKey, e.getMessage());
            throw new BusinessException(CODE_STORAGE, "storage_error");
        }
    }

    private void uploadToMinio(String objectKey, MultipartFile file) throws Exception {
        var minio = storageProperties.getMinio();
        MinioClient client = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(minio.getBucket()).build());
        if (!exists) client.makeBucket(MakeBucketArgs.builder().bucket(minio.getBucket()).build());
        try (InputStream in = file.getInputStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(minio.getBucket()).object(objectKey)
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }
    }

    private void uploadToLocal(String objectKey, MultipartFile file) throws Exception {
        Path target = Paths.get(storageProperties.getLocal().getBaseDir()).resolve(objectKey.replace("/", File.separator));
        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String generateThumbnail(String objectKey, MultipartFile file) {
        try {
            BufferedImage src = ImageIO.read(file.getInputStream());
            if (src == null) return null;

            double ratio = (double) src.getWidth() / src.getHeight();
            int tw = THUMBNAIL_WIDTH;
            int th = (int) (THUMBNAIL_WIDTH / ratio);
            if (th > THUMBNAIL_HEIGHT) { th = THUMBNAIL_HEIGHT; tw = (int) (THUMBNAIL_HEIGHT * ratio); }

            BufferedImage thumb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, tw, th, null);
            g.dispose();

            String ext = objectKey.substring(objectKey.lastIndexOf('.'));
            String thumbKey = objectKey.replace("/images/", "/thumbnails/") + ".jpg";
            Path thumbDir = Paths.get(storageProperties.getLocal().getBaseDir())
                    .resolve(thumbKey.replace("/", File.separator)).getParent();
            Files.createDirectories(thumbDir);
            Path thumbPath = Paths.get(storageProperties.getLocal().getBaseDir())
                    .resolve(thumbKey.replace("/", File.separator));
            ImageIO.write(thumb, "jpg", thumbPath.toFile());

            if ("minio".equalsIgnoreCase(storageProperties.getType())) {
                uploadThumbnailToMinio(thumbKey, thumbBytes(thumb));
            }
            log.debug("Thumbnail generated: {}", thumbKey);
            return thumbKey;
        } catch (Exception e) {
            log.warn("Thumbnail generation failed for {}: {}", objectKey, e.getMessage());
            return null;
        }
    }

    private byte[] thumbBytes(BufferedImage img) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try { ImageIO.write(img, "jpg", baos); } catch (IOException e) { return new byte[0]; }
        return baos.toByteArray();
    }

    private void uploadThumbnailToMinio(String thumbKey, byte[] data) throws Exception {
        var minio = storageProperties.getMinio();
        MinioClient client = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        client.putObject(PutObjectArgs.builder()
                .bucket(minio.getBucket()).object(thumbKey)
                .stream(in, data.length, -1)
                .contentType("image/jpeg")
                .build());
    }

    private int[] readImageDimensions(MultipartFile file) {
        try {
            BufferedImage img = ImageIO.read(file.getInputStream());
            if (img != null) return new int[]{img.getWidth(), img.getHeight()};
        } catch (Exception e) { log.debug("Could not read dimensions: {}", e.getMessage()); }
        return new int[]{0, 0};
    }

    private String resolveExt(String contentType, String filename) {
        if (contentType != null) {
            return switch (contentType.toLowerCase()) {
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                case "image/gif" -> ".gif";
                case "video/mp4" -> ".mp4";
                case "video/webm" -> ".webm";
                case "video/quicktime" -> ".mov";
                default -> ".jpg";
            };
        }
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'));
        }
        return ".jpg";
    }

    private MediaUploadResponse buildResponse(MediaAsset ma) {
        String baseUrl = storageProperties.getLocal().getPublicBaseUrl().replace("/files/cover", "/files/media");
        String url = baseUrl + "/" + ma.getObjectKey();
        String thumbUrl = ma.getThumbnailKey() != null ? baseUrl + "/" + ma.getThumbnailKey() : null;
        return MediaUploadResponse.builder()
                .id(ma.getId())
                .workspaceId(ma.getWorkspaceId())
                .mediaType(ma.getMediaType())
                .url(url)
                .thumbnailUrl(thumbUrl)
                .filename(ma.getFilename())
                .fileSize(ma.getFileSize())
                .contentType(ma.getContentType())
                .width(ma.getWidth())
                .height(ma.getHeight())
                .durationSec(ma.getDurationSec())
                .status(ma.getStatus())
                .scanStatus(ma.getScanStatus())
                .createdAt(ma.getCreatedAt())
                .build();
    }
}