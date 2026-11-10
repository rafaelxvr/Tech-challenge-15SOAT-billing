package com.oficina.billing.adapter.out.mercadopago;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Sondas adversariais da allowlist de mensagem/código do provedor: o que passa e, sobretudo, o que é omitido. */
class ErroProvedorMercadoPagoTest {
    private static final List<String> SEGREDOS = List.of("segredo-literal-xyz");

    private static Optional<String> msg(String texto) { return ErroProvedorMercadoPago.sanitizar(texto, SEGREDOS); }

    private static ErroProvedorMercadoPago corpo(String json) { return ErroProvedorMercadoPago.de(json, SEGREDOS); }

    @Test void mensagemInocuaPassaIntacta() {
        assertThat(msg("Payment cannot be refunded")).contains("Payment cannot be refunded");
    }

    @Test void controlesC0C1ESeparadoresUnicodeViramEspaco() {
        String entrada = "a" + (char) 0x85 + "b" + (char) 0x2028 + "c" + (char) 0x2029 + "d" + (char) 0 + "e" + (char) 0x9b
                + "f" + (char) 0x200b + "g\r\nh";
        assertThat(msg(entrada)).contains("a b c d e f g h");
    }

    @Test void c1NaoSobraNaSaida() {
        for (char c = 0x80; c <= 0x9F; c++) {
            assertThat(msg("x" + c + "y")).contains("x y");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "fim\" mensagemProvedor=\"forjada",
            "barra\\invertida",
            "aspa ' simples",
            "contato joao.silva@example.com",
            "cpf 123.456.789-09",
            "documento 12345678909",
            "telefone (11) 91234-5678",
            "ligar 11 91234 5678",
            "password: hunter2",
            "senha=abc",
            "secret: x",
            "api_key abc",
            "client_secret abc",
            "id 123e4567-e89b-12d3-a456-426614174000",
            "id 123e4567e89b12d3a456426614174000",
            "chave 0123456789abcdef0123",
            "usar segredo-literal-xyz aqui",
            "https://api.mercadopago.com/v1/payments",
            "Bearer abc.def",
            "APP_USR-123-abc",
            "colchetes [31m"})
    void textoSensivelOuForaDaAllowlistEOmitidoPorInteiro(String texto) {
        assertThat(msg(texto)).isEmpty();
    }

    @Test void mensagemLongaETruncada() {
        String s = msg("z".repeat(500)).orElseThrow();
        assertThat(s).hasSize(ErroProvedorMercadoPago.LIMITE_MENSAGEM + 3).endsWith("...");
    }

    @Test void codigoUuidLikeEOmitido() {
        assertThat(corpo("{\"error\":\"123e4567-e89b-12d3-a456-426614174000\",\"message\":\"ok\"}").codigo()).isEmpty();
        assertThat(corpo("{\"error\":\"123e4567e89b12d3a456426614174000\"}").codigo()).isEmpty();
    }

    @Test void codigoComFormaInvalidaOuSensivelEOmitido() {
        assertThat(corpo("{\"error\":\"a b\"}").codigo()).isEmpty();
        assertThat(corpo("{\"error\":\"a\\\"b\"}").codigo()).isEmpty();
        assertThat(corpo("{\"error\":\"user@example.com\"}").codigo()).isEmpty();
        assertThat(corpo("{\"error\":\"12345678909\"}").codigo()).isEmpty();
        assertThat(corpo("{\"error\":\"password\"}").codigo()).isEmpty();
    }

    @Test void codigoLegitimoPassa() {
        assertThat(corpo("{\"error\":\"bad_request\"}").codigo()).contains("bad_request");
        assertThat(corpo("{\"cause\":[{\"code\":2004}]}").codigo()).contains("2004");
    }

    @Test void corpoInvalidoOuNaoObjetoResultaEmVazio() {
        assertThat(corpo("<html>").mensagem()).isEmpty();
        assertThat(corpo("[1]").codigo()).isEmpty();
        assertThat(corpo(null).codigo()).isEmpty();
    }
}
