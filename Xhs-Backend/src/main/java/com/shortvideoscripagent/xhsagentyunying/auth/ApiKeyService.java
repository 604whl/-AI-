package com.shortvideoscripagent.xhsagentyunying.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ApiKey;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ApiKeyMapper;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

/**
 * API Key 服务：生成、校验、撤销、列表。
 * <p>
 * 设计参考 Stripe：xhs_sk_ 前缀 + 24 字符 base62，仅展示一次。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final int CODE_NOT_FOUND = 40401;
    private static final int CODE_FORBIDDEN = 40301;

    private static final String PREFIX = "xhs_sk_";
    private static final int KEY_LENGTH = 24; // base62 chars
    private static final SecureRandom RANDOM = new SecureRandom();

    /** base62 字符集 */
    private static final char[] BASE62_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private final ApiKeyMapper apiKeyMapper;
    private final WorkspaceService workspaceService;

    /**
     * 生成新 API Key。
     *
     * @return 明文 API Key（仅此处返回，存储后不再可获取）
     */
    @Transactional
    public GeneratedKey generate(String workspaceId, Long createdBy, String name, List<String> scopes) {
        workspaceService.requireWorkspaceMember(createdBy, workspaceId, "ADMIN", "OWNER");

        String rawKey = PREFIX + generateRandomKey();
        String keyHash = sha256(rawKey);

        ApiKey entity = new ApiKey();
        entity.setWorkspaceId(workspaceId);
        entity.setKeyHash(keyHash);
        entity.setPrefix(PREFIX);
        entity.setName(name != null ? name : "API Key");
        entity.setScopes(scopes != null ? scopes : List.of("chat", "analysis"));
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(OffsetDateTime.now());
        apiKeyMapper.insert(entity);

        log.info("API Key created: workspace={} name={} createdBy={}", workspaceId, name, createdBy);

        // 返回明文 key（含前缀供用户复制）
        return new GeneratedKey(rawKey, entity.getId());
    }

    /**
     * 校验 API Key 并返回对应的 workspaceId。
     *
     * @param rawKey 明文 API Key
     * @return workspaceId 或 null（无效/已撤销）
     */
    public String validateAndGetWorkspaceId(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(PREFIX)) {
            return null;
        }
        String keyHash = sha256(rawKey);
        return apiKeyMapper.findByHash(keyHash)
                .map(key -> {
                    // 更新最后使用时间
                    apiKeyMapper.touchLastUsed(key.getId(), OffsetDateTime.now());
                    return key.getWorkspaceId();
                })
                .orElse(null);
    }

    /**
     * 获取 API Key 元数据（不含 keyHash 等敏感字段）。
     */
    public List<ApiKey> listActive(String workspaceId, Long userId) {
        workspaceService.requireWorkspaceMember(userId, workspaceId, "ADMIN", "OWNER");
        return apiKeyMapper.listActiveByWorkspace(workspaceId);
    }

    /**
     * 撤销 API Key。
     */
    @Transactional
    public void revoke(Long userId, Long apiKeyId) {
        ApiKey key = apiKeyMapper.selectById(apiKeyId);
        if (key == null || key.getRevokedAt() != null) {
            throw new BusinessException(CODE_NOT_FOUND, "api_key_not_found");
        }
        workspaceService.requireWorkspaceMember(userId, key.getWorkspaceId(), "ADMIN", "OWNER");
        apiKeyMapper.revoke(apiKeyId, OffsetDateTime.now());
        log.info("API Key revoked: id={} workspace={}", apiKeyId, key.getWorkspaceId());
    }

    /**
     * 生成 24 字符 base62 随机字符串。
     */
    private String generateRandomKey() {
        StringBuilder sb = new StringBuilder(KEY_LENGTH);
        for (int i = 0; i < KEY_LENGTH; i++) {
            sb.append(BASE62_CHARS[RANDOM.nextInt(BASE62_CHARS.length)]);
        }
        return sb.toString();
    }

    /**
     * SHA-256 哈希。
     */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public record GeneratedKey(String rawKey, Long id) {
    }
}
