package com.shortvideoscripagent.xhsagentyunying.common.exception;

import com.shortvideoscripagent.xhsagentyunying.common.api.ApiResponse;
import com.shortvideoscripagent.xhsagentyunying.infra.upload.VirusFoundException;
import com.shortvideoscripagent.xhsagentyunying.infra.upload.VirusScanUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int CODE_VIRUS = 4001;
    private static final int CODE_VIRUS_SCAN_UNAVAILABLE = 4003;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        HttpStatus status = mapHttpStatus(ex.getCode());
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
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
        // 403xx = 权限 / 授权门禁（CONNECTOR_NOT_AUTHORIZED 等）
        if (code >= 40300 && code < 40400) {
            return HttpStatus.FORBIDDEN;
        }
        // 404xx = 资源不存在（越权一律按"未找到"处理，也落在这里）
        if (code >= 40400 && code < 40500) {
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

    /**
     * 缺少必填的查询参数（如 {@code ?workspaceId=} 漏传）。
     * <p>
     * Spring 默认把它当未处理异常，最终落到 {@link #handleUnknown} 返回 500，
     * 但这本质上是调用方参数错误，应返回 400，否则前端无法区分
     * 「参数写错了」和「服务端坏了」。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        return ApiResponse.fail(40001, "missing_parameter:" + ex.getParameterName());
    }

    /**
     * 路径变量类型不匹配（如 {@code /templates/not-a-uuid}）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ApiResponse.fail(40001, "invalid_parameter:" + ex.getName());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnknown(Exception ex) {
        // 必须记录堆栈：否则未预期异常会被静默吞掉，线上只能看到 50001/internal_error，
        // 无法定位根因（阶段 3 联调时排查 3 个接口 500 就是卡在这里）。
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ApiResponse.fail(50001, "internal_error");
    }
}
