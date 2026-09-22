package com.greenhill.coop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.common.enums.ProductStatus;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.entity.Product;
import com.greenhill.coop.entity.Round;
import com.greenhill.coop.mapper.MemberMapper;
import com.greenhill.coop.mapper.OrderLineMapper;
import com.greenhill.coop.mapper.OrderMapper;
import com.greenhill.coop.mapper.ProductMapper;
import com.greenhill.coop.mapper.RoundMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class ApiTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected MemberMapper memberMapper;
    @Autowired protected ProductMapper productMapper;
    @Autowired protected RoundMapper roundMapper;
    @Autowired protected OrderMapper orderMapper;
    @Autowired protected OrderLineMapper orderLineMapper;

    protected Member createMember(String memberNo, String name, MemberRole role, String password) {
        Member m = new Member();
        m.setMemberNo(memberNo);
        m.setName(name);
        m.setRole(role);
        m.setStatus(MemberStatus.ACTIVE);
        m.setPasswordHash(passwordEncoder.encode(password));
        memberMapper.insert(m);
        return m;
    }

    protected Product createProduct(String name, UnitType unitType, String price, String bay) {
        Product p = new Product();
        p.setName(name);
        p.setUnitType(unitType);
        p.setPrice(new BigDecimal(price));
        p.setBay(bay);
        p.setStatus(ProductStatus.ACTIVE);
        productMapper.insert(p);
        return p;
    }

    protected Round createRound(int roundNo, RoundStatus status) {
        Round r = new Round();
        r.setRoundNo(roundNo);
        r.setOrdersOpenAt(LocalDateTime.now().minusDays(2));
        r.setOrdersCloseAt(LocalDateTime.now().plusDays(2));
        r.setPickupDate(LocalDate.now().plusDays(5));
        r.setStatus(status);
        roundMapper.insert(r);
        return r;
    }

    protected String tokenFor(String memberNo, String password) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("memberNo", memberNo, "password", password))))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("token").asText();
    }
}
