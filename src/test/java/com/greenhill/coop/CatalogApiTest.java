package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogApiTest extends ApiTestBase {

    private String memberToken;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        memberToken = tokenFor("M-094", "coop1234");
    }

    @Test
    void withoutOpenRoundReturnsNullRoundAndEmptyList() throws Exception {
        createRound(33, RoundStatus.PACKED);
        createProduct("Rolled oats", UnitType.PER_KG, "3.40", "B1");
        mockMvc.perform(get("/api/products/available").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.round", org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.products").isEmpty());
    }

    @Test
    void returnsOnlyActiveProductsWithPricingInfo() throws Exception {
        createRound(34, RoundStatus.OPEN);
        createProduct("Rolled oats, organic", UnitType.PER_KG, "3.40", "B1");
        createProduct("Tahini, 375g jar", UnitType.PER_UNIT, "9.80", "A2");
        Product withdrawn = createProduct("Raw almonds", UnitType.PER_KG, "18.90", "C1");
        withdrawn.setStatus(com.greenhill.coop.common.enums.ProductStatus.WITHDRAWN);
        productMapper.updateById(withdrawn);

        mockMvc.perform(get("/api/products/available").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.round.roundNo").value(34))
            .andExpect(jsonPath("$.data.products.length()").value(2))
            .andExpect(jsonPath("$.data.products[0].name").value("Rolled oats, organic"))
            .andExpect(jsonPath("$.data.products[0].unitType").value("PER_KG"))
            .andExpect(jsonPath("$.data.products[0].price").value(3.40))
            .andExpect(jsonPath("$.data.products[1].name").value("Tahini, 375g jar"));
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/products/available"))
            .andExpect(status().isUnauthorized());
    }
}
