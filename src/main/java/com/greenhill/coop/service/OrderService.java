package com.greenhill.coop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.common.enums.OrderStatus;
import com.greenhill.coop.common.enums.ProductStatus;
import com.greenhill.coop.dto.OrderLineRequest;
import com.greenhill.coop.dto.OrderLineView;
import com.greenhill.coop.dto.OrderView;
import com.greenhill.coop.dto.PlaceOrderRequest;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.entity.Order;
import com.greenhill.coop.entity.OrderLine;
import com.greenhill.coop.entity.Product;
import com.greenhill.coop.entity.Round;
import com.greenhill.coop.mapper.MemberMapper;
import com.greenhill.coop.mapper.OrderLineMapper;
import com.greenhill.coop.mapper.OrderMapper;
import com.greenhill.coop.mapper.ProductMapper;
import com.greenhill.coop.mapper.RoundMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderLineMapper orderLineMapper;
    private final ProductMapper productMapper;
    private final MemberMapper memberMapper;
    private final RoundMapper roundMapper;
    private final RoundService roundService;
    private final PricingService pricingService;

    @Transactional
    public OrderView placeMyOrder(Long memberId, PlaceOrderRequest request) {
        return place(memberId, request);
    }

    @Transactional
    public OrderView placeForMember(Long memberId, PlaceOrderRequest request) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw BizException.notFound("Member not found");
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw BizException.badRequest("Member is inactive");
        }
        return place(memberId, request);
    }

    public List<OrderView> myOrders(Long memberId) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
            .eq(Order::getMemberId, memberId)
            .orderByDesc(Order::getCreatedAt));
        return toViews(orders);
    }

    public List<OrderView> roundOrders(Long roundId) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
            .eq(Order::getRoundId, roundId)
            .eq(Order::getStatus, OrderStatus.ACTIVE)
            .orderByAsc(Order::getId));
        return toViews(orders);
    }

    private OrderView place(Long memberId, PlaceOrderRequest request) {
        Round round = roundService.currentOpen();
        if (round == null) {
            throw BizException.conflict("No round is open for ordering");
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            throw BizException.badRequest("Order must contain at least one line");
        }

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
            .eq(Order::getMemberId, memberId)
            .eq(Order::getRoundId, round.getId()));
        if (order == null) {
            order = new Order();
            order.setMemberId(memberId);
            order.setRoundId(round.getId());
            order.setStatus(OrderStatus.ACTIVE);
            orderMapper.insert(order);
        } else {
            order.setStatus(OrderStatus.ACTIVE);
            orderMapper.updateById(order);
            orderLineMapper.delete(new LambdaQueryWrapper<OrderLine>().eq(OrderLine::getOrderId, order.getId()));
        }

        Set<Long> seen = new HashSet<>();
        for (OrderLineRequest lineRequest : request.lines()) {
            if (!seen.add(lineRequest.productId())) {
                throw BizException.badRequest("Duplicate product in order");
            }
            Product product = productMapper.selectById(lineRequest.productId());
            if (product == null) {
                throw BizException.notFound("Product not found");
            }
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw BizException.badRequest(product.getName() + " is not available");
            }
            OrderLine line = new OrderLine();
            line.setOrderId(order.getId());
            line.setProductId(product.getId());
            line.setQuantity(lineRequest.quantity());
            line.setUnitTypeSnapshot(product.getUnitType());
            line.setUnitPrice(product.getPrice());
            line.setLineTotal(pricingService.lineTotal(product.getUnitType(), lineRequest.quantity(), product.getPrice()));
            orderLineMapper.insert(line);
        }
        return toViews(List.of(order)).get(0);
    }

    private List<OrderView> toViews(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, Round> rounds = roundMapper.selectBatchIds(
                orders.stream().map(Order::getRoundId).distinct().toList())
            .stream().collect(Collectors.toMap(Round::getId, Function.identity()));
        Map<Long, Member> members = memberMapper.selectBatchIds(
                orders.stream().map(Order::getMemberId).distinct().toList())
            .stream().collect(Collectors.toMap(Member::getId, Function.identity()));

        List<OrderLine> allLines = orderLineMapper.selectList(new LambdaQueryWrapper<OrderLine>()
            .in(OrderLine::getOrderId, orders.stream().map(Order::getId).toList())
            .orderByAsc(OrderLine::getId));
        Map<Long, List<OrderLine>> linesByOrder = allLines.stream()
            .collect(Collectors.groupingBy(OrderLine::getOrderId));
        Map<Long, Product> products = allLines.isEmpty() ? Map.of()
            : productMapper.selectBatchIds(allLines.stream().map(OrderLine::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderView> views = new ArrayList<>();
        for (Order order : orders) {
            List<OrderLine> lines = linesByOrder.getOrDefault(order.getId(), List.of());
            BigDecimal total = pricingService.orderTotal(lines);
            List<OrderLineView> lineViews = lines.stream()
                .map(l -> new OrderLineView(l.getId(), l.getProductId(),
                    products.containsKey(l.getProductId()) ? products.get(l.getProductId()).getName() : "(removed)",
                    l.getUnitTypeSnapshot(), l.getQuantity(), l.getUnitPrice(), l.getLineTotal()))
                .toList();
            Round round = rounds.get(order.getRoundId());
            Member member = members.get(order.getMemberId());
            views.add(new OrderView(order.getId(), order.getRoundId(), round == null ? null : round.getRoundNo(),
                order.getMemberId(), member == null ? null : member.getMemberNo(),
                member == null ? null : member.getName(), order.getStatus(), total, lineViews, order.getCreatedAt()));
        }
        return views;
    }
}
