package com.greenhill.coop;

import com.greenhill.coop.mapper.MemberMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CoopApplicationTests {

    @Autowired
    private MemberMapper memberMapper;

    @Test
    void contextLoadsAndSchemaIsCreated() {
        assertThat(memberMapper.selectCount(null)).isZero();
    }
}
