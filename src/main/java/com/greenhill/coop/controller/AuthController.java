package com.greenhill.coop.controller;

import com.greenhill.coop.auth.CurrentUser;
import com.greenhill.coop.auth.MemberContext;
import com.greenhill.coop.common.Result;
import com.greenhill.coop.dto.LoginRequest;
import com.greenhill.coop.dto.LoginResponse;
import com.greenhill.coop.dto.MemberView;
import com.greenhill.coop.service.AuthService;
import com.greenhill.coop.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MemberService memberService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/me")
    public Result<MemberView> me(@CurrentUser MemberContext context) {
        return Result.success(memberService.getView(context.id()));
    }
}
