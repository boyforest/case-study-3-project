package com.greenhill.coop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.greenhill.coop.entity.OrderLine;
import com.greenhill.coop.dto.RoundTotalRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface OrderLineMapper extends BaseMapper<OrderLine> {

    @Select("""
        SELECT p.id AS productId,
               p.name AS productName,
               ol.unit_type_snapshot AS unitType,
               SUM(ol.quantity) AS totalQuantity,
               SUM(ol.line_total) AS totalAmount
        FROM order_line ol
        JOIN orders o ON o.id = ol.order_id
        JOIN product p ON p.id = ol.product_id
        WHERE o.round_id = #{roundId} AND o.status = 'ACTIVE'
        GROUP BY p.id, p.name, ol.unit_type_snapshot
        ORDER BY p.name
        """)
    List<RoundTotalRow> selectRoundTotals(@Param("roundId") Long roundId);
}
