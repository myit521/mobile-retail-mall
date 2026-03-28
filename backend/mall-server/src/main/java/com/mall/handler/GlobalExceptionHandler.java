package com.mall.handler;

import com.mall.constant.MessageConstant;
import com.mall.exception.BaseException;
import com.mall.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    /**
     * 捕获全局异常，返回错误信息
     * @param ex
     * @return
     */
    @ExceptionHandler(Exception.class)
    public Result exceptionHandler(Exception ex) {
        log.error("系统异常", ex);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : MessageConstant.UNKNOWN_ERROR;
        log.warn("业务异常：{}", message);
        return Result.error(message);
    }
    /**
     * 捕获sql异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException  ex){
        log.error("数据库约束异常", ex);
        String message = ex.getMessage();
        // 匹配 "Duplicate entry 'value' for key" 格式
        Pattern pattern = Pattern.compile("Duplicate entry '([^']+)' for key");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            String duplicateValue = matcher.group(1);
            return Result.error( duplicateValue + MessageConstant.ALREADY_EXISTS);
        }
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

    /**
     * 参数校验异常（@Valid / @Validated）
     * @param ex
     * @return
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result exceptionHandler(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult() != null && ex.getBindingResult().hasErrors()
                ? ex.getBindingResult().getAllErrors().get(0).getDefaultMessage()
                : MessageConstant.INVALID_PARAM;
        log.warn("参数校验失败：{}", message);
        return Result.error(message);
    }

    /**
     * 参数校验异常（@RequestParam / @PathVariable）
     * @param ex
     * @return
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result exceptionHandler(ConstraintViolationException ex) {
        String message = ex.getMessage();
        log.warn("参数校验失败：{}", message);
        return Result.error(MessageConstant.INVALID_PARAM);
    }

    /**
     * 捕获数字格式化异常（参数类型转换错误）
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(NumberFormatException ex) {
        log.warn("参数类型转换错误：{}", ex.getMessage());
        return Result.error("参数类型错误，请检查输入是否正确");
    }

}
