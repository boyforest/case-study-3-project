package com.greenhill.coop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.PageResult;
import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.dto.MemberCreateRequest;
import com.greenhill.coop.dto.MemberUpdateRequest;
import com.greenhill.coop.dto.MemberView;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<MemberView> page(String keyword, MemberStatus status, long page, long size) {
        LambdaQueryWrapper<Member> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Member::getName, keyword).or().like(Member::getMemberNo, keyword));
        }
        if (status != null) {
            qw.eq(Member::getStatus, status);
        }
        qw.orderByAsc(Member::getMemberNo);
        Page<Member> result = memberMapper.selectPage(new Page<>(page, size), qw);
        List<MemberView> views = result.getRecords().stream().map(MemberView::from).toList();
        return PageResult.of(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public MemberView getView(Long id) {
        return MemberView.from(find(id));
    }

    public MemberView create(MemberCreateRequest request) {
        Long existing = memberMapper.selectCount(new LambdaQueryWrapper<Member>()
            .eq(Member::getMemberNo, request.memberNo()));
        if (existing > 0) {
            throw BizException.conflict("Member number already exists");
        }
        Member member = new Member();
        member.setMemberNo(request.memberNo());
        member.setName(request.name());
        member.setPhone(request.phone());
        member.setEmail(request.email());
        member.setAddress(request.address());
        member.setRole(MemberRole.MEMBER);
        member.setStatus(MemberStatus.ACTIVE);
        member.setPasswordHash(passwordEncoder.encode(request.password()));
        memberMapper.insert(member);
        return MemberView.from(member);
    }

    public MemberView update(Long id, MemberUpdateRequest request) {
        Member member = find(id);
        member.setName(request.name());
        member.setPhone(request.phone());
        member.setEmail(request.email());
        member.setAddress(request.address());
        memberMapper.updateById(member);
        return MemberView.from(member);
    }

    public MemberView deactivate(Long id, Long currentUserId) {
        if (id.equals(currentUserId)) {
            throw BizException.badRequest("You cannot deactivate your own account");
        }
        return setStatus(id, MemberStatus.INACTIVE);
    }

    public MemberView activate(Long id) {
        return setStatus(id, MemberStatus.ACTIVE);
    }

    public void resetPassword(Long id, String password) {
        Member member = find(id);
        member.setPasswordHash(passwordEncoder.encode(password));
        memberMapper.updateById(member);
    }

    private MemberView setStatus(Long id, MemberStatus status) {
        Member member = find(id);
        member.setStatus(status);
        memberMapper.updateById(member);
        return MemberView.from(member);
    }

    private Member find(Long id) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw BizException.notFound("Member not found");
        }
        return member;
    }
}
