package com.demandforecast.exception;

import com.demandforecast.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ApiError.FieldError> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fe -> ApiError.FieldError.builder()
                .field(fe.getField())
                .rejectedValue(fe.getRejectedValue())
                .message(fe.getDefaultMessage())
                .build())
            .toList();

        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Validation Failed",
                     "One or more fields failed validation", request, null, fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String msg = String.format("Parameter '%s' should be of type %s",
                ex.getName(), ex.getRequiredType() != null
                        ? ex.getRequiredType().getSimpleName() : "unknown");
        return build(HttpStatus.BAD_REQUEST, "Type Mismatch", msg, request, null, null);
    }

    @ExceptionHandler(PredictionNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            PredictionNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(),
                     request, ex.getErrorCode(), null);
    }

    @ExceptionHandler(BatchSizeExceededException.class)
    public ResponseEntity<ApiError> handleBatchTooLarge(
            BatchSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Batch Too Large", ex.getMessage(),
                     request, ex.getErrorCode(), null);
    }

    @ExceptionHandler(MlApiUnavailableException.class)
    public ResponseEntity<ApiError> handleMlUnavailable(
            MlApiUnavailableException ex, HttpServletRequest request) {
        log.error("ML API unavailable: {}", ex.getMessage(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "ML Service Unavailable",
                     ex.getMessage(), request, ex.getErrorCode(), null);
    }

    @ExceptionHandler(MlApiException.class)
    public ResponseEntity<ApiError> handleMlError(
            MlApiException ex, HttpServletRequest request) {
        log.error("ML API error: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "ML Service Error",
                     ex.getMessage(), request, ex.getErrorCode(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                     "An unexpected error occurred", request, "INTERNAL_ERROR", null);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status, String error, String message,
            HttpServletRequest request, String requestId,
            List<ApiError.FieldError> fieldErrors) {

        String reqId = requestId != null ? requestId
                : request.getHeader("X-Request-ID");

        ApiError body = ApiError.builder()
            .status(status.value())
            .error(error)
            .message(message)
            .path(request.getRequestURI())
            .requestId(reqId)
            .timestamp(Instant.now())
            .fieldErrors(fieldErrors)
            .build();

        return ResponseEntity.status(status).body(body);
    }
}
