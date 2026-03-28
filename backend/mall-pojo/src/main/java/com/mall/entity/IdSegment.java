package com.mall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 号段表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdSegment {
    private String bizTag;
    private Long maxId;
    private Integer step;
    private LocalDateTime updateTime;
}
