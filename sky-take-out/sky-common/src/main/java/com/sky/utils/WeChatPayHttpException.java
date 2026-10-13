package com.sky.utils;

import java.io.IOException;

public class WeChatPayHttpException extends IOException {
    private final int status;
    private final String code;

    public WeChatPayHttpException(int status, String code) {
        super("WeChat Pay HTTP " + status + (code == null ? "" : " (" + code + ")"));
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }

    public String getCode() { return code; }
}
