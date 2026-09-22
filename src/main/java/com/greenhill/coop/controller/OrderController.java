package com.greenhill.coop.controller;

import com.greenhill.coop.auth.CurrentUser;
import com.greenhill.coop.auth.MemberContext;
import com.greenhill.coop.common.Result;
import com.greenhill.coop.dto.OrderView;
import com.greenhill.coop.dto.PlaceOrderRequest;
import com.greenhill.coop.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/mine")
    public Result<List<OrderView>> myOrders(@CurrentUser MemberContext context) {
        return Result.success(orderService.myOrders(context.id()));
    }

    @PutMapping("/mine")
    public Result<OrderView> placeMine(@CurrentUser MemberContext context,
                                       @Valid @RequestBody PlaceOrderRequest request) {
        return Result.success(orderService.placeMyOrder(context.id(), request));
    }
}
