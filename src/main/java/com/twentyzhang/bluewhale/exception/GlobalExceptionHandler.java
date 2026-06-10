package com.twentyzhang.bluewhale.exception;

import com.twentyzhang.bluewhale.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Validation 校验失败 → 400，将所有字段错误拼接后返回 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(Result.CODE_BAD_REQUEST, message);
    }

    /** 请求体无法解析（非法 JSON / 非 UTF-8 / 类型不匹配）→ 400，而非落入通用 500 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMostSpecificCause().getMessage());
        return Result.badRequest("请求体格式错误或无法解析");
    }

    /** 业务异常 → 使用异常内的 code */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        log.warn("业务异常 [{}]: {}", ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    /** 其他运行时异常 → 500，隐藏内部细节 */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException ex) {
        log.error("未预期运行时异常", ex);
        return Result.fail(Result.CODE_SERVER_ERROR, "服务器内部错误，请稍后重试");
    }
}
