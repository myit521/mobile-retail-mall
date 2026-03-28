package com.mall.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 备忘录DTO - 用于创建/更新备忘录
 */
@Data
public class MemoDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键ID（更新时使用）
     */
    private Long id;
    
    /**
     * 原始内容（必填，支持自然语言输入）
     */
    private String content;
    
    /**
     * 优先级：0-普通，1-重要，2-紧急
     */
    private Integer priority;
    
    /**
     * 截止时间（手动指定，会覆盖AI解析结果）
     */
    private LocalDateTime dueDate;
    
    /**
     * 提醒时间
     */
    private LocalDateTime remindTime;
    
    /**
     * 标签（逗号分隔）
     */
    private String tags;
    
    /**
     * 是否启用AI解析（默认true）
     */
    private Boolean enableAiParse = true;
}
