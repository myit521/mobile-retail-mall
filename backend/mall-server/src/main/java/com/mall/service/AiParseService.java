package com.mall.service;

import com.mall.vo.MemoParseResultVO;

/**
 * AI文本解析服务接口
 */
public interface AiParseService {
    
    /**
     * 解析备忘录文本
     * 从自然语言文本中提取：标题、截止时间、优先级、标签、关键任务点等结构化信息
     * 
     * @param content 原始文本内容
     * @return 解析结果VO
     */
    MemoParseResultVO parseMemoContent(String content);
    
    /**
     * 检查AI服务是否可用
     * @return true-可用，false-不可用
     */
    boolean isServiceAvailable();
}
