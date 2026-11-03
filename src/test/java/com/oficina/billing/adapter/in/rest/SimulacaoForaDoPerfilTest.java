package com.oficina.billing.adapter.in.rest;

import com.oficina.billing.support.TokensTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Control S-5: without the gateway-fake profile the simulation endpoint does not exist, not even for ADMIN. */
@SpringBootTest(properties = "billing.mercadopago.access-token=token-de-teste-sem-valor-real")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SimulacaoForaDoPerfilTest {
    @Autowired MockMvc mvc;

    @Test void semPerfilFakeOEndpointDeSimulacaoRetorna404() throws Exception {
        mvc.perform(post("/billing/simulacoes/pagamentos/" + UUID.randomUUID()).contextPath("/billing")
                        .header("Authorization", "Bearer " + TokensTeste.staff("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"approved\"}"))
                .andExpect(status().isNotFound());
    }
}
