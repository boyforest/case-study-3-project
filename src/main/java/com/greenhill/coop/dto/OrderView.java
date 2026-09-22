package com.greenhill.coop.dto;

import com.greenhill.coop.common.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderView(Long id, Long roundId, Integer roundNo, Long memberId, String memberNo, String memberName,
                        OrderStatus status, BigDecimal total, List<OrderLineView> lines, LocalDateTime createdAt) {
}
