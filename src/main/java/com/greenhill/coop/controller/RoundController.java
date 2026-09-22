package com.greenhill.coop.controller;

import com.greenhill.coop.auth.RequireCoordinator;
import com.greenhill.coop.common.PageResult;
import com.greenhill.coop.common.Result;
import com.greenhill.coop.dto.RoundCreateRequest;
import com.greenhill.coop.dto.RoundView;
import com.greenhill.coop.service.RoundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rounds")
@RequiredArgsConstructor
public class RoundController {

    private final RoundService roundService;

    @GetMapping("/current")
    public Result<RoundView> current() {
        var round = roundService.currentOpen();
        return Result.success(round == null ? null : RoundView.from(round));
    }

    @GetMapping
    @RequireCoordinator
    public Result<PageResult<RoundView>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.success(roundService.page(page, size));
    }

    @PostMapping
    @RequireCoordinator
    public Result<RoundView> create(@Valid @RequestBody RoundCreateRequest request) {
        return Result.success(roundService.create(request));
    }

    @PostMapping("/{id}/close")
    @RequireCoordinator
    public Result<RoundView> close(@PathVariable Long id) {
        return Result.success(roundService.close(id));
    }

    @PostMapping("/{id}/pack")
    @RequireCoordinator
    public Result<RoundView> pack(@PathVariable Long id) {
        return Result.success(roundService.pack(id));
    }

    @GetMapping("/{id}/totals")
    @RequireCoordinator
    public Result<com.greenhill.coop.dto.RoundTotalsView> totals(@PathVariable Long id) {
        return Result.success(roundService.roundTotals(id));
    }
}
