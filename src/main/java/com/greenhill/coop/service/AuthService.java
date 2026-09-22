package com.greenhill.coop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenhill.coop.auth.JwtUtil;
import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.dto.LoginRequest;
import com.greenhill.coop.dto.LoginResponse;
import com.greenhill.coop.dto.MemberView;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest request) {
        Member member = memberMapper.selectOne(new LambdaQueryWrapper<Member>()
            .eq(Member::getMemberNo, request.memberNo()));
        if (member == null || !passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw BizException.badRequest("Invalid member number or password");
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw BizException.badRequest("This account is inactive");
        }
        return new LoginResponse(jwtUtil.generateToken(member.getId()), MemberView.from(member));
    }
}
