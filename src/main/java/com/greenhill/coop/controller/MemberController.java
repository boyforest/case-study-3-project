package com.greenhill.coop.controller;

import com.greenhill.coop.auth.CurrentUser;
import com.greenhill.coop.auth.MemberContext;
import com.greenhill.coop.auth.RequireCoordinator;
import com.greenhill.coop.common.PageResult;
import com.greenhill.coop.common.Result;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.dto.MemberCreateRequest;
import com.greenhill.coop.dto.MemberUpdateRequest;
import com.greenhill.coop.dto.MemberView;
import com.greenhill.coop.dto.ResetPasswordRequest;
import com.greenhill.coop.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@RequireCoordinator
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public Result<PageResult<MemberView>> page(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) MemberStatus status,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return Result.success(memberService.page(keyword, status, page, size));
    }

    @PostMapping
    public Result<MemberView> create(@Valid @RequestBody MemberCreateRequest request) {
        return Result.success(memberService.create(request));
    }

    @PutMapping("/{id}")
    public Result<MemberView> update(@PathVariable Long id, @Valid @RequestBody MemberUpdateRequest request) {
        return Result.success(memberService.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public Result<MemberView> deactivate(@PathVariable Long id, @CurrentUser MemberContext context) {
        return Result.success(memberService.deactivate(id, context.id()));
    }

    @PostMapping("/{id}/activate")
    public Result<MemberView> activate(@PathVariable Long id) {
        return Result.success(memberService.activate(id));
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        memberService.resetPassword(id, request.password());
        return Result.success(null);
    }
}
