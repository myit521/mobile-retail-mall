package com.mall.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 备忘录分页查询DTO
 */
@Data
public class MemoPageQueryDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 页码
     */
    private Integer page;
    
    /**
     * 每页数量
     */
    private Integer pageSize;
    
    /**
     * 状态：0-待处理，1-处理中，2-已完成，3-已取消
     */
    private Integer status;
    
    /**
     * 优先级：0-普通，1-重要，2-紧急
     */
    private Integer priority;
    
    /**
     * 关键词搜索（标题/内容）
     */
    private String keyword;
    
    /**
     * 标签过滤
     */
    private String tag;
}
