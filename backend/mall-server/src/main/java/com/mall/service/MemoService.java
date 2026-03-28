package com.mall.service;

import com.mall.dto.MemoDTO;
import com.mall.dto.MemoPageQueryDTO;
import com.mall.result.PageResult;
import com.mall.vo.MemoParseResultVO;
import com.mall.vo.MemoVO;

/**
 * 备忘录服务接口
 */
public interface MemoService {
    
    /**
     * 创建备忘录
     * @param memoDTO 备忘录DTO
     * @return 备忘录VO
     */
    MemoVO create(MemoDTO memoDTO);
    
    /**
     * 更新备忘录
     * @param memoDTO 备忘录DTO
     */
    void update(MemoDTO memoDTO);
    
    /**
     * 更新备忘录状态
     * @param id 备忘录ID
     * @param status 新状态
     */
    void updateStatus(Long id, Integer status);
    
    /**
     * 删除备忘录
     * @param id 备忘录ID
     */
    void delete(Long id);
    
    /**
     * 根据ID查询备忘录
     * @param id 备忘录ID
     * @return 备忘录VO
     */
    MemoVO getById(Long id);
    
    /**
     * 分页查询备忘录
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    PageResult pageQuery(MemoPageQueryDTO queryDTO);
    
    /**
     * 仅解析文本（预览AI解析结果）
     * @param content 文本内容
     * @return 解析结果
     */
    MemoParseResultVO parseOnly(String content);
    
    /**
     * 获取备忘录统计
     * @return 各状态数量统计
     */
    MemoStatsVO getStats();
    
    /**
     * 备忘录统计VO（内部类）
     */
    class MemoStatsVO {
        public Integer pendingCount;    // 待处理
        public Integer processingCount; // 处理中
        public Integer completedCount;  // 已完成
        public Integer cancelledCount;  // 已取消
        public Integer totalCount;      // 总数
    }
}
