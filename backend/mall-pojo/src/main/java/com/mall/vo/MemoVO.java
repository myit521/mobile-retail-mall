package com.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 备忘录VO - 用于返回备忘录详情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 标题（AI解析生成）
     */
    private String title;
    
    /**
     * 原始内容
     */
    private String content;
    
    /**
     * AI解析后的结构化内容（JSON格式）
     */
    private String parsedContent;
    
    /**
     * 优先级：0-普通，1-重要，2-紧急
     */
    private Integer priority;
    
    /**
     * 优先级描述
     */
    private String priorityDesc;
    
    /**
     * 状态：0-待处理，1-处理中，2-已完成，3-已取消
     */
    private Integer status;
    
    /**
     * 状态描述
     */
    private String statusDesc;
    
    /**
     * 截止时间
     */
    private LocalDateTime dueDate;
    
    /**
     * 提醒时间
     */
    private LocalDateTime remindTime;
    
    /**
     * 是否已提醒
     */
    private Integer isReminded;
    
    /**
     * 标签（逗号分隔）
     */
    private String tags;
    
    /**
     * 是否已过期
     */
    private Boolean isOverdue;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
