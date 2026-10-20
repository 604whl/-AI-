package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.media.MediaUploadResponse;
import com.shortvideoscripagent.xhsagentyunying.service.MediaStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private static final int CODE_UNAUTHORIZED = 40101;
    private final MediaStorageService mediaStorageService;

    @PostMapping("/upload")
    public ApiResponse<MediaUploadResponse> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "assetId", required = false) String assetId
    ) {
        Long userId = requireUserId();
        return ApiResponse.ok(mediaStorageService.uploadMedia(userId, file, assetId));
    }

    @GetMapping("/{id}")
    public ApiResponse<MediaUploadResponse> getMedia(@PathVariable Long id) {
        Long userId = requireUserId();
        return ApiResponse.ok(mediaStorageService.getMedia(userId, id));
    }

    @GetMapping("/list")
    public ApiResponse<List<MediaUploadResponse>> listMedia() {
        Long userId = requireUserId();
        return ApiResponse.ok(mediaStorageService.listMedia(userId));
    }

    @GetMapping("/asset/{assetId}")
    public ApiResponse<List<MediaUploadResponse>> listByAsset(@PathVariable String assetId) {
        Long userId = requireUserId();
        return ApiResponse.ok(mediaStorageService.listByAsset(userId, UUID.fromString(assetId)));
    }

    private Long requireUserId() {
        Long uid = RequestContext.getUserId();
        if (uid == null) throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        return uid;
    }
}