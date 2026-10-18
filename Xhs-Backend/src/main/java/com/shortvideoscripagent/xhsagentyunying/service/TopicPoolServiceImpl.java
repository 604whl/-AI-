package com.shortvideoscripagent.xhsagentyunying.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAssetVersion;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.TopicPool;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetVersionMapper;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.TopicPoolMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.asset.AssetCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicCreateRequest;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicDto;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicListResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.topic.TopicUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicPoolServiceImpl implements TopicPoolService {

    private static final int CODE_NOT_FOUND = 40410;
    private static final int CODE_BAD_REQUEST = 40010;
    private static final int CODE_CONFLICT = 40910;

    private final TopicPoolMapper topicMapper;
    private final ContentAssetMapper assetMapper;
    private final ContentAssetVersionMapper versionMapper;

    @Override
    public TopicListResponse list(Long workspaceId, String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        long offset = (safePage - 1L) * safeSize;

        Page<TopicPool> p = new Page<>(safePage, safeSize);
        IPage<TopicPool> result = topicMapper.selectPageByWorkspace(
                p, workspaceId, status, offset, safeSize);
        long total = result.getTotal();

        List<TopicDto> items = result.getRecords().stream()
                .map(this::toDto)
                .toList();

        return new TopicListResponse(items, total, safePage, safeSize);
    }

    @Override
    @Transactional
    public TopicDto create(Long workspaceId, Long userId, TopicCreateRequest req) {
        TopicPool topic = new TopicPool();
        topic.setWorkspaceId(workspaceId);
        topic.setTitle(req.getTitle().trim());
        topic.setSource(req.getSource().trim());
        topic.setHeatScore(req.getHeatScore() != null ? req.getHeatScore() : 0);
        topic.setTags(req.getTags());
        topic.setTargetPersona(req.getTargetPersona());
        topic.setScheduledDate(req.getScheduledDate());
        topic.setStatus(TopicPool.STATUS_ACTIVE);
        topic.setCreatedBy(userId);
        topic.setCreatedAt(OffsetDateTime.now());
        topic.setUpdatedAt(OffsetDateTime.now());
        topicMapper.insert(topic);

        log.info("Topic {} created by user {} in workspace {}", topic.getId(), userId, workspaceId);
        return toDto(topic);
    }

    @Override
    @Transactional
    public TopicDto update(Long topicId, Long workspaceId, TopicUpdateRequest req) {
        TopicPool topic = requireOwnedTopic(topicId, workspaceId);

        if (req.getStatus() != null) {
            validateStatusTransition(topic.getStatus(), req.getStatus());
            topic.setStatus(req.getStatus());
        }
        if (req.getScheduledDate() != null) {
            topic.setScheduledDate(req.getScheduledDate());
        }
        if (req.getTags() != null) {
            topic.setTags(req.getTags());
        }

        topic.setUpdatedAt(OffsetDateTime.now());
        topicMapper.updateById(topic);

        return toDto(topic);
    }

    @Override
    @Transactional
    public ToAssetResponse toAsset(Long topicId, Long workspaceId, Long userId, String platform, String category) {
        TopicPool topic = requireOwnedTopic(topicId, workspaceId);
        if (!TopicPool.STATUS_ACTIVE.equals(topic.getStatus())) {
            throw new BusinessException(CODE_CONFLICT, "topic_not_active");
        }

        AssetCreateRequest assetReq = new AssetCreateRequest();
        assetReq.setTitle(topic.getTitle());
        assetReq.setBody("");
        assetReq.setPlatform(platform);
        assetReq.setScenario(ContentAsset.STATUS_DRAFT);

        ContentAsset asset = createDraftInternal(userId, workspaceId, assetReq);

        topic.setStatus(TopicPool.STATUS_USED);
        topic.setUpdatedAt(OffsetDateTime.now());
        topicMapper.updateById(topic);

        UUID versionId = versionMapper.selectByAssetIdAndVersion(asset.getId(), 1)
                .map(v -> v.getId())
                .orElse(null);

        log.info("Topic {} converted to asset {} by user {}", topicId, asset.getId(), userId);
        return new ToAssetResponse(asset.getId(), versionId);
    }

    private TopicPool requireOwnedTopic(Long topicId, Long workspaceId) {
        TopicPool topic = topicMapper.selectById(topicId);
        if (topic == null) {
            throw new BusinessException(CODE_NOT_FOUND, "topic_not_found");
        }
        if (!workspaceId.equals(topic.getWorkspaceId())) {
            throw new BusinessException(CODE_NOT_FOUND, "topic_not_found");
        }
        return topic;
    }

    private void validateStatusTransition(String current, String target) {
        if (TopicPool.STATUS_ACTIVE.equals(current)
                && (TopicPool.STATUS_USED.equals(target) || TopicPool.STATUS_ARCHIVED.equals(target))) {
            return;
        }
        if (TopicPool.STATUS_USED.equals(current) && TopicPool.STATUS_ARCHIVED.equals(target)) {
            return;
        }
        if (TopicPool.STATUS_ARCHIVED.equals(current)) {
            throw new BusinessException(CODE_BAD_REQUEST, "archived_topic_immutable");
        }
        throw new BusinessException(CODE_BAD_REQUEST,
                "invalid_status_transition: " + current + " -> " + target);
    }

    private ContentAsset createDraftInternal(Long ownerId, Long workspaceId, AssetCreateRequest request) {
        OffsetDateTime now = OffsetDateTime.now();

        ContentAsset asset = new ContentAsset();
        asset.setId(UUID.randomUUID());
        asset.setWorkspaceId(UUID.fromString(String.valueOf(workspaceId)));
        asset.setOwnerUserId(ownerId);
        asset.setTitle(request.getTitle() != null ? request.getTitle().trim() : "");
        asset.setBody(request.getBody() != null ? request.getBody() : "");
        asset.setPersona(request.getPersona());
        asset.setScenario(request.getScenario() != null ? request.getScenario() : ContentAsset.STATUS_DRAFT);
        asset.setIndustry(request.getIndustry());
        asset.setStatus(ContentAsset.STATUS_DRAFT);
        asset.setCurrentVersion(1);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        assetMapper.insert(asset);

        ContentAssetVersion version = new ContentAssetVersion();
        version.setId(UUID.randomUUID());
        version.setAssetId(asset.getId());
        version.setVersionNo(1);
        version.setTitle(asset.getTitle());
        version.setBody(asset.getBody());
        version.setEditedBy(ownerId);
        version.setEditReason("initial draft from topic");
        version.setCreatedAt(now);
        versionMapper.insert(version);

        return asset;
    }

    private TopicDto toDto(TopicPool topic) {
        return TopicDto.builder()
                .id(topic.getId())
                .workspaceId(topic.getWorkspaceId())
                .title(topic.getTitle())
                .source(topic.getSource())
                .heatScore(topic.getHeatScore())
                .status(topic.getStatus())
                .tags(topic.getTags())
                .targetPersona(topic.getTargetPersona())
                .createdBy(topic.getCreatedBy())
                .createdAt(topic.getCreatedAt())
                .updatedAt(topic.getUpdatedAt())
                .scheduledDate(topic.getScheduledDate())
                .build();
    }
}