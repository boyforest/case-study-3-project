package com.greenhill.coop;

import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.OrderLine;
import com.greenhill.coop.service.PricingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingServiceTest {

    private final PricingService pricingService = new PricingService();

    private OrderLine line(String quantity, String unitPrice) {
        OrderLine line = new OrderLine();
        line.setQuantity(new BigDecimal(quantity));
        line.setUnitPrice(new BigDecimal(unitPrice));
        line.setLineTotal(new BigDecimal(quantity).multiply(new BigDecimal(unitPrice))
            .setScale(2, java.math.RoundingMode.HALF_UP));
        return line;
    }

    @Test
    void perUnitLineIsCountTimesPrice() {
        assertThat(pricingService.lineTotal(UnitType.PER_UNIT, new BigDecimal("2"), new BigDecimal("7.50")))
            .isEqualByComparingTo("15.00");
    }

    @Test
    void perKgLineIsWeightTimesPricePerKg() {
        assertThat(pricingService.lineTotal(UnitType.PER_KG, new BigDecimal("1.5"), new BigDecimal("3.40")))
            .isEqualByComparingTo("5.10");
    }

    @Test
    void perKgAcceptsQuarterKilogram() {
        assertThat(pricingService.lineTotal(UnitType.PER_KG, new BigDecimal("0.25"), new BigDecimal("32.00")))
            .isEqualByComparingTo("8.00");
    }

    @Test
    void perKgRoundsHalfUp() {
        assertThat(pricingService.lineTotal(UnitType.PER_KG, new BigDecimal("0.5"), new BigDecimal("4.85")))
            .isEqualByComparingTo("2.43");
    }

    @Test
    void perKgUsesActualPackedWeightStyleDecimals() {
        assertThat(pricingService.lineTotal(UnitType.PER_KG, new BigDecimal("1.58"), new BigDecimal("3.40")))
            .isEqualByComparingTo("5.37");
    }

    @Test
    void perUnitRejectsFractionalQuantity() {
        assertThatThrownBy(() -> pricingService.lineTotal(UnitType.PER_UNIT, new BigDecimal("1.5"), new BigDecimal("9.80")))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("whole number");
    }

    @Test
    void perKgRejectsMoreThanThreeDecimals() {
        assertThatThrownBy(() -> pricingService.lineTotal(UnitType.PER_KG, new BigDecimal("0.1234"), new BigDecimal("3.40")))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("3 decimal");
    }

    @Test
    void rejectsZeroAndNegativeQuantity() {
        assertThatThrownBy(() -> pricingService.lineTotal(UnitType.PER_KG, BigDecimal.ZERO, new BigDecimal("3.40")))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> pricingService.lineTotal(UnitType.PER_UNIT, new BigDecimal("-1"), new BigDecimal("7.50")))
            .isInstanceOf(BizException.class);
    }

    @Test
    void kyTranRound33OrderTotalsTo54Dollars85() {
        List<OrderLine> lines = List.of(
            line("1.5", "3.40"),
            line("2", "4.10"),
            line("1", "4.85"),
            line("0.25", "32.00"),
            line("1", "9.80"),
            line("2", "7.50"),
            line("1.5", "2.60")
        );
        assertThat(pricingService.orderTotal(lines)).isEqualByComparingTo("54.85");
    }
}
