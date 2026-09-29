package com.sky.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.io.Serializable;

@Data
@AllArgsConstructor
public class ErrorResponse implements Serializable {
    private String code;
    private String message;
    private String traceId;
}
