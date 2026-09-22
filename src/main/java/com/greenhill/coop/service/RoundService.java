package com.greenhill.coop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.PageResult;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.dto.RoundCreateRequest;
import com.greenhill.coop.dto.RoundView;
import com.greenhill.coop.entity.Round;
import com.greenhill.coop.mapper.RoundMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoundService {

    private final RoundMapper roundMapper;

    public PageResult<RoundView> page(long page, long size) {
        Page<Round> result = roundMapper.selectPage(new Page<>(page, size),
            new LambdaQueryWrapper<Round>().orderByDesc(Round::getRoundNo));
        List<RoundView> views = result.getRecords().stream().map(RoundView::from).toList();
        return PageResult.of(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public RoundView create(RoundCreateRequest request) {
        if (roundMapper.selectCount(new LambdaQueryWrapper<Round>().eq(Round::getRoundNo, request.roundNo())) > 0) {
            throw BizException.conflict("Round number already exists");
        }
        if (currentOpen() != null) {
            throw BizException.conflict("Another round is still open");
        }
        Round round = new Round();
        round.setRoundNo(request.roundNo());
        round.setOrdersOpenAt(request.ordersOpenAt());
        round.setOrdersCloseAt(request.ordersCloseAt());
        round.setPickupDate(request.pickupDate());
        round.setStatus(RoundStatus.OPEN);
        roundMapper.insert(round);
        return RoundView.from(round);
    }

    public RoundView close(Long id) {
        return transition(id, RoundStatus.OPEN, RoundStatus.CLOSED, "Only an open round can be closed");
    }

    public RoundView pack(Long id) {
        return transition(id, RoundStatus.CLOSED, RoundStatus.PACKED, "Only a closed round can be packed");
    }

    public Round currentOpen() {
        return roundMapper.selectList(new LambdaQueryWrapper<Round>()
                .eq(Round::getStatus, RoundStatus.OPEN))
            .stream().findFirst().orElse(null);
    }

    private RoundView transition(Long id, RoundStatus from, RoundStatus to, String errorMessage) {
        Round round = find(id);
        if (round.getStatus() != from) {
            throw BizException.conflict(errorMessage);
        }
        round.setStatus(to);
        roundMapper.updateById(round);
        return RoundView.from(round);
    }

    private Round find(Long id) {
        Round round = roundMapper.selectById(id);
        if (round == null) {
            throw BizException.notFound("Round not found");
        }
        return round;
    }
}
