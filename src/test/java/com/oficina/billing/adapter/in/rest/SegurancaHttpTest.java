package com.oficina.billing.adapter.in.rest;

import com.oficina.billing.application.ConfirmacaoPagamentoService;
import com.oficina.billing.support.TokensTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "gateway-fake"})
class SegurancaHttpTest {
    @Autowired MockMvc mvc;
    @MockBean ConfirmacaoPagamentoService confirmacao;
    @MockBean com.oficina.billing.application.ReconsultaPagamentoService reconsulta;

    static String assinatura(String dataId, String requestId, long ts) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("segredo-webhook-teste".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String manifesto = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        return "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void saudeEPublica() throws Exception {
        mvc.perform(get("/billing/actuator/health").contextPath("/billing")).andExpect(status().isOk());
    }

    @Test void consultaExigeAdmin() throws Exception {
        String url = "/billing/orcamentos?osId=" + UUID.randomUUID();
        mvc.perform(get(url).contextPath("/billing")).andExpect(status().isUnauthorized());
        mvc.perform(get(url).contextPath("/billing").header("Authorization", "Bearer " + TokensTeste.staff("MECANICO")))
                .andExpect(status().isForbidden());
        mvc.perform(get(url).contextPath("/billing").header("Authorization", "Bearer " + TokensTeste.staff("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test void tokenDeClienteOuComEscoposERejeitado() throws Exception {
        String url = "/billing/orcamentos?osId=" + UUID.randomUUID();
        mvc.perform(get(url).contextPath("/billing").header("Authorization", "Bearer " + TokensTeste.cliente()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(url).contextPath("/billing").header("Authorization",
                "Bearer " + TokensTeste.staff("ADMIN", Map.of("scopes", "orders:read:self"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(url).contextPath("/billing").header("Authorization", "Bearer lixo"))
                .andExpect(status().isUnauthorized());
    }

    @Test void reconsultaExigeAdmin() throws Exception {
        String url = "/billing/pagamentos/" + UUID.randomUUID() + "/reconsulta";
        mvc.perform(post(url).contextPath("/billing")).andExpect(status().isUnauthorized());
        mvc.perform(post(url).contextPath("/billing").header("Authorization", "Bearer " + TokensTeste.staff("MECANICO")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reconsulta);
    }

    @Test void reconsultaDePagamentoInexistenteRetorna404() throws Exception {
        mvc.perform(post("/billing/pagamentos/" + UUID.randomUUID() + "/reconsulta").contextPath("/billing")
                .header("Authorization", "Bearer " + TokensTeste.staff("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test void pagamentoInexistenteRetorna404() throws Exception {
        mvc.perform(get("/billing/pagamentos/" + UUID.randomUUID()).contextPath("/billing")
                .header("Authorization", "Bearer " + TokensTeste.staff("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test void webhookSemAssinaturaValidaRetorna401() throws Exception {
        mvc.perform(post("/billing/webhooks/mercadopago").contextPath("/billing").param("data.id", "123").param("type", "payment")
                .header("x-request-id", "req-1").header("x-signature", "ts=1,v1=00"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(confirmacao);
    }

    @Test void webhookAssinadoProcessaPagamento() throws Exception {
        long ts = Instant.now().getEpochSecond();
        mvc.perform(post("/billing/webhooks/mercadopago").contextPath("/billing").param("data.id", "123").param("type", "payment")
                .header("x-request-id", "req-1").header("x-signature", assinatura("123", "req-1", ts)))
                .andExpect(status().isOk());
        verify(confirmacao).processarNotificacao("123");
    }

    @Test void replayDoMesmoEventoAssinadoNaoReprocessa() throws Exception {
        long ts = Instant.now().getEpochSecond();
        String requestId = "req-replay-" + UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/billing/webhooks/mercadopago").contextPath("/billing").param("data.id", "777").param("type", "payment")
                    .header("x-request-id", requestId).header("x-signature", assinatura("777", requestId, ts)))
                    .andExpect(status().isOk());
        }
        verify(confirmacao, times(1)).processarNotificacao("777");
    }

    @Test void webhookComCorpoAcimaDoLimiteRetorna413() throws Exception {
        long ts = Instant.now().getEpochSecond();
        mvc.perform(post("/billing/webhooks/mercadopago").contextPath("/billing").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"payment\",\"pad\":\"" + "x".repeat(20_000) + "\"}")
                .header("x-request-id", "req-grande").header("x-signature", assinatura("1", "req-grande", ts)))
                .andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(confirmacao);
    }

    @Test void webhookDeOutroTipoEIgnorado() throws Exception {
        long ts = Instant.now().getEpochSecond();
        mvc.perform(post("/billing/webhooks/mercadopago").contextPath("/billing").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"merchant_order\",\"data\":{\"id\":\"55\"}}")
                .header("x-request-id", "req-2").header("x-signature", assinatura("55", "req-2", ts)))
                .andExpect(status().isOk());
        verify(confirmacao, never()).processarNotificacao(anyString());
    }

    @Test void simulacaoExigeTokenAdmin() throws Exception {
        String url = "/billing/simulacoes/pagamentos/" + UUID.randomUUID();
        mvc.perform(post(url).contextPath("/billing").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"approved\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(url).contextPath("/billing").header("Authorization", "Bearer " + TokensTeste.staff("MECANICO"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"approved\"}"))
                .andExpect(status().isForbidden());
        verify(confirmacao, never()).processarNotificacao(anyString());
    }

    @Test void simulacaoDisponivelNoPerfilFake() throws Exception {
        UUID pagamento = UUID.randomUUID();
        String admin = "Bearer " + TokensTeste.staff("ADMIN");
        mvc.perform(post("/billing/simulacoes/pagamentos/" + pagamento).contextPath("/billing").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"approved\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mpPaymentId").isString());
        verify(confirmacao).processarNotificacao(anyString());
        mvc.perform(post("/billing/simulacoes/pagamentos/" + pagamento).contextPath("/billing").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"talvez\"}"))
                .andExpect(status().isBadRequest());
    }
}
