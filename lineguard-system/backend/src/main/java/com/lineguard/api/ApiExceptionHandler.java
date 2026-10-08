package com.lineguard.api;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        detail.setTitle("LineGuard 操作未完成");
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + "：" + error.getDefaultMessage())
                .orElse("请求参数不符合要求");
        return problem(HttpStatus.BAD_REQUEST, "请求参数错误", message);
    }

    /** 同一任务的并发指令会撞上 command_receipts 唯一约束，这里返回冲突而不是 500。 */
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ProblemDetail handleConcurrentWrite(Exception exception) {
        log.warn("任务指令与并发请求冲突", exception);
        return problem(HttpStatus.CONFLICT, "LineGuard 操作未完成",
                "该指令与同一任务的并发请求冲突，请刷新任务状态后重试");
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ProblemDetail handleBadRequest(Exception exception) {
        log.warn("请求参数无法处理", exception);
        return problem(HttpStatus.BAD_REQUEST, "请求参数错误", "请求参数不合法或无法解析");
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException exception) {
        log.error("服务内部状态异常", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "LineGuard 内部错误", "服务内部状态异常，请查看后端日志");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
