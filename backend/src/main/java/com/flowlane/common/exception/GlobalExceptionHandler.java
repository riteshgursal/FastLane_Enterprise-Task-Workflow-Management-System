package com.flowlane.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Maps every error to RFC 9457 Problem Details with a stable {@code code}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static ProblemDetail problem(ErrorCode code, String detail, String path) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), detail);
        pd.setTitle(code.title());
        pd.setType(URI.create("https://flowlane.dev/problems/" + code.name().toLowerCase().replace('_', '-')));
        pd.setProperty("code", code.name());
        if (path != null) {
            pd.setInstance(URI.create(path));
        }
        return pd;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException ex, HttpServletRequest req) {
        return ResponseEntity.status(ex.code().status()).body(problem(ex.code(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        ProblemDetail pd = problem(ErrorCode.VALIDATION_FAILED, "Check the highlighted fields.", req.getRequestURI());
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> badRequest(Exception ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(problem(ErrorCode.BAD_REQUEST, "The request is malformed.", req.getRequestURI()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> conflict(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return ResponseEntity.status(409).body(problem(ErrorCode.VERSION_CONFLICT, "Reload to see the latest changes.", req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(403).body(problem(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.title(), req.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> tooBig(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return ResponseEntity.status(422).body(problem(ErrorCode.FILE_REJECTED, "Files can be up to 25 MB.", req.getRequestURI()));
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class,
            MissingServletRequestParameterException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ProblemDetail> framework(Exception ex, HttpServletRequest req) {
        int status = ((ErrorResponse) ex).getStatusCode().value();
        ErrorCode code = status == 404 ? ErrorCode.NOT_FOUND : ErrorCode.BAD_REQUEST;
        ProblemDetail pd = problem(code, status == 404 ? "No such endpoint." : "The request is not supported.", req.getRequestURI());
        pd.setStatus(status);
        return ResponseEntity.status(status).body(pd);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error on {}", req.getRequestURI(), ex);
        return ResponseEntity.status(500).body(problem(ErrorCode.INTERNAL_ERROR, "Something went wrong. Try again.", req.getRequestURI()));
    }
}
