package com.shortvideoscripagent.xhsagentyunying.billing.invoice;

import com.shortvideoscripagent.xhsagentyunying.config.AppStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * S4-4 发票文件存储服务。
 * <p>
 * <b>存储布局</b>：{@code invoices/{workspaceId}/{invoiceNo}.pdf}。
 * </p>
 * <p>
 * <b>下载链接</b>：
 * <ul>
 *   <li><b>MinIO 模式</b>：预签名 URL，5 分钟过期（私有桶语义）；</li>
 *   <li><b>本地模式</b>：由 {@code InvoiceDownloadController} 用 HMAC 签名 URL 承载，
 *       同样 5 分钟过期（签名在 URL 上，服务端校验过期时间与签名）。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceStorageService {

    /** 预签名 / 签名 URL 有效期（秒）= 5 分钟 */
    public static final int DOWNLOAD_URL_EXPIRY_SECONDS = 300;

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String OBJECT_PREFIX = "invoices/";

    private final AppStorageProperties storageProperties;

    /**
     * 上传发票 PDF。
     *
     * @param workspaceId 工作区
     * @param invoiceNo   发票号
     * @param pdf         PDF 字节
     * @return 对象 key
     */
    public String store(String workspaceId, String invoiceNo, byte[] pdf) {
        String objectKey = OBJECT_PREFIX + workspaceId + "/" + invoiceNo + ".pdf";
        try {
            if ("minio".equalsIgnoreCase(storageProperties.getType())) {
                storeToMinio(objectKey, pdf);
            } else {
                storeToLocal(objectKey, pdf);
            }
            return objectKey;
        } catch (Exception ex) {
            log.error("Failed to store invoice pdf {}: {}", objectKey, ex.getMessage(), ex);
            throw new IllegalStateException("invoice_pdf_store_failed", ex);
        }
    }

    /**
     * 读取发票 PDF。
     */
    public byte[] load(String objectKey) {
        try {
            if ("minio".equalsIgnoreCase(storageProperties.getType())) {
                return loadFromMinio(objectKey);
            }
            return loadFromLocal(objectKey);
        } catch (Exception ex) {
            log.error("Failed to load invoice pdf {}: {}", objectKey, ex.getMessage(), ex);
            throw new IllegalStateException("invoice_pdf_load_failed", ex);
        }
    }

    /**
     * 生成下载 URL（MinIO 预签名；本地模式返回 null，由签名下载接口承载）。
     *
     * @param objectKey 对象 key
     * @return 下载 URL；本地模式返回 null
     */
    public String presignedDownloadUrl(String objectKey) {
        if (!"minio".equalsIgnoreCase(storageProperties.getType())) {
            return null;
        }
        try {
            return minioClient().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(storageProperties.getMinio().getBucket())
                    .object(objectKey)
                    .method(Method.GET)
                    .expiry(DOWNLOAD_URL_EXPIRY_SECONDS)
                    .build());
        } catch (Exception ex) {
            log.warn("Presigned URL generation failed for key={}: {}", objectKey, ex.getMessage());
            return null;
        }
    }

    /**
     * 对象 key 是否合法（防路径穿越）。
     */
    public boolean isValidObjectKey(String objectKey) {
        return objectKey != null
                && objectKey.startsWith(OBJECT_PREFIX)
                && !objectKey.contains("..");
    }

    public String pdfContentType() {
        return PDF_CONTENT_TYPE;
    }

    // ── private ─────────────────────────────────────────────────────────

    private void storeToMinio(String objectKey, byte[] pdf) throws Exception {
        var minio = storageProperties.getMinio();
        MinioClient client = minioClient();
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(minio.getBucket()).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(minio.getBucket()).build());
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(pdf)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(minio.getBucket())
                    .object(objectKey)
                    .stream(in, pdf.length, -1)
                    .contentType(PDF_CONTENT_TYPE)
                    .build());
        }
    }

    private void storeToLocal(String objectKey, byte[] pdf) throws Exception {
        Path target = resolveLocalPath(objectKey);
        Files.createDirectories(target.getParent());
        Files.copy(new ByteArrayInputStream(pdf), target, StandardCopyOption.REPLACE_EXISTING);
    }

    private byte[] loadFromMinio(String objectKey) throws Exception {
        var minio = storageProperties.getMinio();
        try (var response = minioClient().getObject(GetObjectArgs.builder()
                .bucket(minio.getBucket())
                .object(objectKey)
                .build())) {
            return response.readAllBytes();
        }
    }

    private byte[] loadFromLocal(String objectKey) throws Exception {
        Path path = resolveLocalPath(objectKey);
        if (!Files.exists(path)) {
            throw new IllegalStateException("invoice_pdf_not_found");
        }
        return Files.readAllBytes(path);
    }

    private Path resolveLocalPath(String objectKey) {
        return Paths.get(storageProperties.getLocal().getBaseDir())
                .resolve(objectKey.replace("/", java.io.File.separator));
    }

    private MinioClient minioClient() {
        var minio = storageProperties.getMinio();
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }

    /**
     * 由发票号推导对象 key（避免到处拼字符串）。
     */
    public static String objectKeyOf(String workspaceId, String invoiceNo) {
        return (OBJECT_PREFIX + workspaceId + "/" + invoiceNo + ".pdf")
                .toLowerCase(Locale.ROOT);
    }
}
