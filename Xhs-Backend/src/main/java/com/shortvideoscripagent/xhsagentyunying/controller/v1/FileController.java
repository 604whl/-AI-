package com.shortvideoscripagent.xhsagentyunying.controller.v1;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.api.RequestContext;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.file.CoverUploadResponse;
import com.shortvideoscripagent.xhsagentyunying.service.CoverStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/files")
@RequiredArgsConstructor
public class FileController {

    private static final int CODE_UNAUTHORIZED = 40101;

    private final CoverStorageService coverStorageService;

    @PostMapping("/cover")
    public ApiResponse<CoverUploadResponse> uploadCover(@RequestParam("file") MultipartFile file) {
        Long userId = requireUserId();
        return ApiResponse.ok(coverStorageService.upload(userId, file));
    }

    @GetMapping("/cover/{*objectKey}")
    public ResponseEntity<byte[]> getCover(@PathVariable String objectKey) {
        requireUserId();
        String normalized = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        CoverStorageService.StoredObject stored = coverStorageService.load(normalized);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .body(stored.bytes());
    }

    private Long requireUserId() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            throw new BusinessException(CODE_UNAUTHORIZED, "unauthorized");
        }
        return userId;
    }
}
