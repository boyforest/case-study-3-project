package com.greenhill.coop.service;

import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.OrderLine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PricingService {

    public BigDecimal lineTotal(UnitType unitType, BigDecimal quantity, BigDecimal unitPrice) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("Quantity must be greater than zero");
        }
        if (unitType == UnitType.PER_UNIT && quantity.stripTrailingZeros().scale() > 0) {
            throw BizException.badRequest("This product is sold by the unit; quantity must be a whole number");
        }
        if (unitType == UnitType.PER_KG && quantity.stripTrailingZeros().scale() > 3) {
            throw BizException.badRequest("This product is sold by weight; use at most 3 decimal places");
        }
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal orderTotal(List<OrderLine> lines) {
        return lines.stream()
            .map(OrderLine::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }
}
