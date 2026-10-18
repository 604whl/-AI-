package com.shortvideoscripagent.xhsagentyunying.common.exception;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.common.exception.PlatformConnectorUnavailableException;
import com.shortvideoscripagent.xhsagentyunying.infra.upload.VirusFoundException;
import com.shortvideoscripagent.xhsagentyunying.infra.upload.VirusScanUnavailableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int CODE_VIRUS = 4001;
    private static final int CODE_VIRUS_SCAN_UNAVAILABLE = 4003;
    private static final int CODE_VERSION_CONFLICT = 40910;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        HttpStatus status = mapHttpStatus(ex.getCode());
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    /**
     * M0-5：平台连接器不可用，返回 503 Service Unavailable。
     */
    @ExceptionHandler(PlatformConnectorUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformConnectorUnavailable(
            PlatformConnectorUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(VirusFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleVirusFound(VirusFoundException ex) {
        return ApiResponse.fail(CODE_VIRUS, "virus_detected");
    }

    @ExceptionHandler(VirusScanUnavailableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleVirusScanUnavailable(VirusScanUnavailableException ex) {
        return ApiResponse.fail(CODE_VIRUS_SCAN_UNAVAILABLE, "virus_scan_unavailable");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(CODE_VERSION_CONFLICT, "version_conflict"));
    }

    private HttpStatus mapHttpStatus(int code) {
        if (code == 40101) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == 40301) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == 40401) {
            return HttpStatus.NOT_FOUND;
        }
        if (code >= 40900 && code < 41000) {
            return HttpStatus.CONFLICT;
        }
        if (code >= 42900 && code < 43000) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (code >= 50400 && code < 50500) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if (code >= 50000 && code < 60000) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ApiResponse.fail(40002, msg.isEmpty() ? "validation_failed" : msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnknown(Exception ex) {
        return ApiResponse.fail(50001, "internal_error");
    }
}
