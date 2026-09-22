package com.greenhill.coop.dto;

import java.math.BigDecimal;
import java.util.List;

public record RoundTotalsView(Long roundId, Integer roundNo, List<RoundTotalRow> rows, BigDecimal totalAmount) {
}
