package com.mall.mapper;

import com.mall.dto.StockAlertPageQueryDTO;
import com.mall.entity.StockAlert;
import com.mall.vo.StockAlertVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockAlertMapper {

    /**
     * 插入预警记录
     * @param stockAlert
     */
    void insert(StockAlert stockAlert);

    /**
     * 分页查询预警记录（关联处理人姓名）
     * @param stockAlertPageQueryDTO
     * @return
     */
    List<StockAlertVO> selectPage(StockAlertPageQueryDTO stockAlertPageQueryDTO);

    /**
     * 更新预警状态
     * @param stockAlert
     */
    void update(StockAlert stockAlert);

    /**
     * 查询某商品是否有未处理的预警
     * @param productId
     * @return
     */
    @Select("select count(id) from stock_alert where product_id = #{productId} and alert_status = 0")
    Integer countPendingByProductId(Long productId);
}
