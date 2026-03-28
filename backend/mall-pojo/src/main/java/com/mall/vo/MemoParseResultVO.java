package com.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI解析结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoParseResultVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 解析出的标题
     */
    private String title;
    
    /**
     * 解析出的截止时间
     */
    private LocalDateTime dueDate;
    
    /**
     * 解析出的优先级
     */
    private Integer priority;
    
    /**
     * 解析出的标签
     */
    private List<String> tags;
    
    /**
     * 解析出的关键任务点
     */
    private List<String> keyPoints;
    
    /**
     * AI解析的原始JSON
     */
    private String rawJson;
    
    /**
     * 是否解析成功
     */
    private Boolean success;
    
    /**
     * 错误信息
     */
    private String errorMessage;
}
