package com.classhub.common.exception;

import com.classhub.common.api.ApiError;
import com.classhub.common.api.ApiErrorBody;
import com.classhub.common.api.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorBody> application(
            ApplicationException ex, HttpServletRequest request) {
        return error(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorBody> notFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                ErrorCodes.NOT_FOUND,
                "Resource not found",
                request.getRequestURI(),
                null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorBody> invalidArgument(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldErrorDetail(
                        error.getField(),
                        error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()))
                .toList();
        String message = fieldErrors.isEmpty()
                ? "Request validation failed"
                : fieldErrors.getFirst().field() + " " + fieldErrors.getFirst().message();
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.VALIDATION_ERROR,
                message,
                request.getRequestURI(),
                fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorBody> constraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.VALIDATION_ERROR,
                "Request validation failed",
                request.getRequestURI(),
                null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorBody> unreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.VALIDATION_ERROR,
                "Malformed request body",
                request.getRequestURI(),
                null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorBody> typeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.VALIDATION_ERROR,
                "Invalid request parameter",
                request.getRequestURI(),
                null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorBody> missingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.VALIDATION_ERROR,
                "Missing required request parameter",
                request.getRequestURI(),
                null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorBody> methodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return error(
                HttpStatus.METHOD_NOT_ALLOWED,
                ErrorCodes.VALIDATION_ERROR,
                "HTTP method not allowed",
                request.getRequestURI(),
                null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorBody> mediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return error(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErrorCodes.VALIDATION_ERROR,
                "Unsupported media type",
                request.getRequestURI(),
                null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorBody> tooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.ATTACHMENT_TOO_LARGE,
                "Attachment exceeds the maximum allowed size",
                request.getRequestURI(),
                null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorBody> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception path={}", request.getRequestURI(), ex);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCodes.INTERNAL_ERROR,
                "An unexpected error occurred",
                request.getRequestURI(),
                null);
    }

    private static ResponseEntity<ApiErrorBody> error(
            HttpStatus status,
            String code,
            String message,
            String path,
            List<ApiError.FieldErrorDetail> fieldErrors) {
        return ResponseEntity.status(status).body(ApiErrorBody.of(code, message, path, fieldErrors));
    }
}
