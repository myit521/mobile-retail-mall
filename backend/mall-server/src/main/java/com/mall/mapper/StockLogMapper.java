package com.mall.mapper;

import com.mall.dto.StockLogPageQueryDTO;
import com.mall.entity.StockLog;
import com.mall.vo.StockLogVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface StockLogMapper {

    /**
     * 插入库存变动日志
     * @param stockLog
     */
    void insert(StockLog stockLog);

    /**
     * 分页查询库存日志（关联商品名称、操作人姓名）
     * @param stockLogPageQueryDTO
     * @return
     */
    List<StockLogVO> selectPage(StockLogPageQueryDTO stockLogPageQueryDTO);
}
