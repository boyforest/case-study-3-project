package com.greenhill.coop.auth;

import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.mapper.MemberMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final MemberMapper memberMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw BizException.unauthorized("Not logged in");
        }
        Long memberId;
        try {
            memberId = jwtUtil.getMemberId(header.substring(7));
        } catch (Exception e) {
            throw BizException.unauthorized("Invalid or expired token");
        }
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw BizException.unauthorized("Member not found");
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw BizException.unauthorized("Account is inactive");
        }
        boolean needsCoordinator = handlerMethod.hasMethodAnnotation(RequireCoordinator.class)
            || handlerMethod.getBeanType().isAnnotationPresent(RequireCoordinator.class);
        if (needsCoordinator && member.getRole() != MemberRole.COORDINATOR) {
            throw BizException.forbidden("Coordinator permission required");
        }
        UserContext.set(new MemberContext(member.getId(), member.getMemberNo(), member.getName(), member.getRole()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
