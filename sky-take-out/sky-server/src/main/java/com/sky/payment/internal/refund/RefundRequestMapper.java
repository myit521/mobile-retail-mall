package com.sky.payment.internal.refund;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RefundRequestMapper {
    @Insert("INSERT INTO refund_request(order_id, order_number, out_refund_no, amount, status, created_at, updated_at) "
            + "VALUES(#{orderId}, #{orderNumber}, #{refundNo}, #{amount}, 'PENDING', NOW(), NOW())")
    int insert(@Param("orderId") long orderId, @Param("orderNumber") String orderNumber,
               @Param("refundNo") String refundNo, @Param("amount") BigDecimal amount);

    @Select("SELECT * FROM refund_request WHERE out_refund_no = #{refundNo}")
    RefundRequest find(String refundNo);

    @Select("SELECT * FROM refund_request WHERE out_refund_no = #{refundNo} FOR UPDATE")
    RefundRequest lock(String refundNo);

    @Select("SELECT * FROM refund_request WHERE status IN ('PENDING','PROCESSING') "
            + "AND updated_at < #{before} ORDER BY updated_at LIMIT 100")
    List<RefundRequest> due(LocalDateTime before);

    @Update("UPDATE refund_request SET status = 'PROCESSING', updated_at = NOW() "
            + "WHERE out_refund_no = #{refundNo} AND status = 'PENDING'")
    int markProcessing(String refundNo);

    @Update("UPDATE refund_request SET status = #{status}, updated_at = NOW() "
            + "WHERE out_refund_no = #{refundNo} AND status <> 'SUCCESS'")
    int updateStatus(@Param("refundNo") String refundNo, @Param("status") String status);

    @Update("UPDATE orders SET pay_status = 2 WHERE id = #{orderId} AND pay_status = 1")
    int markOrderRefunded(long orderId);
}
