package com.ucms.base.contract;

import com.ucms.base.KeycloakTestTokens;
import com.ucms.base.client.AcademicStructureClient;
import com.ucms.base.service.BaseService;
import com.ucms.base.web.dto.GradingScaleResponse;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base class of the tests generated from src/test/resources/contracts (one per file).
 * Each contract is what another service expects from this one, so a generated test fails
 * when a response here stops matching what a caller reads.
 *
 * The real controllers, JSON mapping and security run. The service behind them is mocked
 * and returns the fixed answer below, because a contract is about the shape of a
 * response; the behaviour behind it is covered by the other tests of this service.
 * Requests carry a service token, as calls from other services do.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class ContractBase {

    @Autowired WebApplicationContext context;
    @MockBean AcademicStructureClient academicStructureClient;
    @MockBean BaseService baseService;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + KeycloakTestTokens.service("svc:base")))
                .build());

        when(baseService.listGradingScales(any(), any(), any())).thenReturn(new PageImpl<>(List.of(
                new GradingScaleResponse(UUID.fromString("14141414-1414-1414-1414-141414141414"), "Standard",
                        "A:80,B:65,C:50,F:0", "ACTIVE"))));
    }
}
