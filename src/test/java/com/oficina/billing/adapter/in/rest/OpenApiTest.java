package com.oficina.billing.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.billing.support.TokensTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guarda o contrato HTTP publicado em docs/openapi.json. Para regenerar após uma mudança intencional:
 * ./mvnw test -Dtest=OpenApiTest -Dopenapi.escrever=true
 */
@SpringBootTest(properties = "billing.mercadopago.access-token=token-de-teste-sem-valor-real")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiTest {
    static final Path PUBLICADO = Path.of("docs", "openapi.json");
    static final ObjectMapper JSON = new ObjectMapper();
    @Autowired MockMvc mvc;

    @Test void contratoHttpPublicadoCorrespondeAoGerado() throws Exception {
        String gerado = mvc.perform(get("/billing/v3/api-docs").contextPath("/billing")
                        .header("Authorization", "Bearer " + TokensTeste.staff("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode atual = JSON.readTree(gerado);
        assertThat(atual.get("paths").fieldNames()).toIterable().contains("/webhooks/mercadopago", "/orcamentos");
        if (Boolean.getBoolean("openapi.escrever")) {
            Files.createDirectories(PUBLICADO.getParent());
            Files.writeString(PUBLICADO, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(atual) + "\n");
        }
        assertThat(JSON.readTree(Files.readString(PUBLICADO))).isEqualTo(atual);
    }
}
