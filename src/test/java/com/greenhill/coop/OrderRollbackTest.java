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
import com.greenhill.coop.mapper.ProductMapper;
import com.greenhill.coop.mapper.RoundMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:rollbacktest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class OrderRollbackTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MemberMapper memberMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private RoundMapper roundMapper;

    @Test
    void failedReplaceKeepsTheOriginalOrderIntact() throws Exception {
        Member member = new Member();
        member.setMemberNo("M-RB1");
        member.setName("Rollback Tester");
        member.setRole(MemberRole.MEMBER);
        member.setStatus(MemberStatus.ACTIVE);
        member.setPasswordHash(passwordEncoder.encode("coop1234"));
        memberMapper.insert(member);

        Round round = new Round();
        round.setRoundNo(901);
        round.setOrdersOpenAt(LocalDateTime.now().minusDays(1));
        round.setOrdersCloseAt(LocalDateTime.now().plusDays(1));
        round.setPickupDate(LocalDate.now().plusDays(5));
        round.setStatus(RoundStatus.OPEN);
        roundMapper.insert(round);

        Product oats = product("Rollback oats", UnitType.PER_KG, "3.40");
        Product eggs = product("Rollback eggs", UnitType.PER_UNIT, "7.50");

        String token = login("M-RB1");

        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"productId\":%d,\"quantity\":1.5}]}".formatted(oats.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(5.10));

        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"lines":[{"productId":%d,"quantity":2},{"productId":%d,"quantity":1.5}]}
                    """.formatted(oats.getId(), eggs.getId())))
            .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.data[0].total").value(5.10))
            .andExpect(jsonPath("$.data[0].lines.length()").value(1))
            .andExpect(jsonPath("$.data[0].lines[0].quantity").value(1.5));
    }

    private Product product(String name, UnitType unitType, String price) {
        Product product = new Product();
        product.setName(name);
        product.setUnitType(unitType);
        product.setPrice(new BigDecimal(price));
        product.setStatus(ProductStatus.ACTIVE);
        productMapper.insert(product);
        return product;
    }

    private String login(String memberNo) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("memberNo", memberNo, "password", "coop1234"))))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
