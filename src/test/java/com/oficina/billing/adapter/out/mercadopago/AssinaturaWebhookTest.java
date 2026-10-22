package com.oficina.billing.adapter.out.mercadopago;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class AssinaturaWebhookTest {
    static final Instant AGORA = Instant.parse("2026-09-15T12:00:00Z");
    static final String SEGREDO = "segredo-webhook-teste";
    final AssinaturaWebhook assinatura = new AssinaturaWebhook(SEGREDO, Duration.ofMinutes(5), Clock.fixed(AGORA, ZoneOffset.UTC));

    static String hmac(String manifesto) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
    }

    static String cabecalho(long ts, String dataId, String requestId) throws Exception {
        return "ts=" + ts + ",v1=" + hmac("id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";");
    }

    @Test void assinaturaCorretaEValida() throws Exception {
        long ts = AGORA.getEpochSecond();
        assertThat(assinatura.valida(cabecalho(ts, "123456", "req-1"), "req-1", "123456")).isTrue();
    }

    @Test void idAlfanumericoEComparadoEmMinusculas() throws Exception {
        long ts = AGORA.getEpochSecond();
        assertThat(assinatura.valida(cabecalho(ts, "abc123", "req-1"), "req-1", "ABC123")).isTrue();
    }

    @Test void timestampEmMilissegundosEAceito() throws Exception {
        long ts = AGORA.toEpochMilli();
        assertThat(assinatura.valida(cabecalho(ts, "1", "r"), "r", "1")).isTrue();
    }

    @Test void assinaturaForaDaToleranciaERejeitada() throws Exception {
        long antigo = AGORA.minus(Duration.ofMinutes(6)).getEpochSecond();
        assertThat(assinatura.valida(cabecalho(antigo, "1", "r"), "r", "1")).isFalse();
    }

    @Test void hmacErradoOuCamposAusentesSaoRejeitados() throws Exception {
        long ts = AGORA.getEpochSecond();
        assertThat(assinatura.valida(cabecalho(ts, "1", "r"), "outro-request", "1")).isFalse();
        assertThat(assinatura.valida("ts=" + ts + ",v1=00", "r", "1")).isFalse();
        assertThat(assinatura.valida(null, "r", "1")).isFalse();
        assertThat(assinatura.valida("v1=abc", "r", "1")).isFalse();
        assertThat(assinatura.valida("ts=x,v1=abc", "r", "1")).isFalse();
        assertThat(new AssinaturaWebhook("", Duration.ofMinutes(5), Clock.systemUTC()).valida(cabecalho(ts, "1", "r"), "r", "1"))
                .isFalse();
    }
}
