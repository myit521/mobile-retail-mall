package com.mall.mapper;

import com.mall.dto.MemoPageQueryDTO;
import com.mall.entity.Memo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 备忘录Mapper
 */
@Mapper
public interface MemoMapper {
    
    /**
     * 插入备忘录
     * @param memo 备忘录实体
     */
    void insert(Memo memo);
    
    /**
     * 更新备忘录
     * @param memo 备忘录实体
     */
    void update(Memo memo);
    
    /**
     * 根据ID查询
     * @param id 主键ID
     * @return 备忘录实体
     */
    Memo getById(@Param("id") Long id);
    
    /**
     * 根据ID和用户ID查询
     * @param id 主键ID
     * @param userId 用户ID
     * @return 备忘录实体
     */
    Memo getByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
    
    /**
     * 删除备忘录
     * @param id 主键ID
     * @param userId 用户ID
     */
    void deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
    
    /**
     * 分页查询
     * @param queryDTO 查询条件
     * @param userId 用户ID
     * @return 备忘录列表
     */
    List<Memo> pageQuery(@Param("query") MemoPageQueryDTO queryDTO, @Param("userId") Long userId);
    
    /**
     * 查询需要提醒的备忘录
     * @param now 当前时间
     * @return 备忘录列表
     */
    List<Memo> selectNeedRemind(@Param("now") LocalDateTime now);
    
    /**
     * 批量更新提醒状态
     * @param ids ID列表
     */
    void batchUpdateReminded(@Param("ids") List<Long> ids);
    
    /**
     * 查询已过期的待处理备忘录
     * @param now 当前时间
     * @return 备忘录列表
     */
    List<Memo> selectOverdue(@Param("now") LocalDateTime now);
    
    /**
     * 统计用户各状态备忘录数量
     * @param userId 用户ID
     * @param status 状态
     * @return 数量
     */
    Integer countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status);
}
