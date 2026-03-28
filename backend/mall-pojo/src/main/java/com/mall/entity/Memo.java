package com.mall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 备忘录/待办事项实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Memo implements Serializable {
    
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
     * 状态：0-待处理，1-处理中，2-已完成，3-已取消
     */
    private Integer status;
    
    /**
     * 截止时间（AI解析提取）
     */
    private LocalDateTime dueDate;
    
    /**
     * 提醒时间
     */
    private LocalDateTime remindTime;
    
    /**
     * 是否已提醒：0-否，1-是
     */
    private Integer isReminded;
    
    /**
     * 标签（逗号分隔）
     */
    private String tags;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    // 状态常量
    public static final Integer STATUS_PENDING = 0;      // 待处理
    public static final Integer STATUS_PROCESSING = 1;   // 处理中
    public static final Integer STATUS_COMPLETED = 2;    // 已完成
    public static final Integer STATUS_CANCELLED = 3;    // 已取消
    
    // 优先级常量
    public static final Integer PRIORITY_NORMAL = 0;     // 普通
    public static final Integer PRIORITY_IMPORTANT = 1;  // 重要
    public static final Integer PRIORITY_URGENT = 2;     // 紧急
}
