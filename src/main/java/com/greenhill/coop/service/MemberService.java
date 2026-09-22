package com.greenhill.coop.service;

import com.greenhill.coop.common.BizException;
import com.greenhill.coop.dto.MemberView;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;

    public MemberView getView(Long id) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw BizException.notFound("Member not found");
        }
        return MemberView.from(member);
    }
}
