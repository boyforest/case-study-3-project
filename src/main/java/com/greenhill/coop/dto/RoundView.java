package com.greenhill.coop.dto;

import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.entity.Round;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RoundView(Long id, Integer roundNo, LocalDateTime ordersOpenAt, LocalDateTime ordersCloseAt,
                        LocalDate pickupDate, RoundStatus status) {

    public static RoundView from(Round r) {
        return new RoundView(r.getId(), r.getRoundNo(), r.getOrdersOpenAt(), r.getOrdersCloseAt(),
            r.getPickupDate(), r.getStatus());
    }
}
