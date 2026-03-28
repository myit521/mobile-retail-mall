package com.mall.exception;

/**
 * 登录频率限制异常（账号被临时锁定）
 */
public class TooManyAttemptsException extends BaseException {

    public TooManyAttemptsException() {
        super();
    }

    public TooManyAttemptsException(String msg) {
        super(msg);
    }
}
