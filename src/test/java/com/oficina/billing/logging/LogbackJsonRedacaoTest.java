package com.oficina.billing.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** Exercita o logback-spring.xml real sob perfil não local: mensagem formatada presente e segredo/PII mascarados. Só sentinelas fictícias. */
class LogbackJsonRedacaoTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MASCARA = "[REDACTED]";

    @Test
    void emiteMensagemFormatadaRealSemConstanteSafe() throws Exception {
        JsonNode json = emitir(false, "Evento de billing registrado: {}", "evento_sentinela_ficticio");

        assertThat(json.get("message").asText()).isEqualTo("Evento de billing registrado: evento_sentinela_ficticio");
        assertThat(json.get("message").asText()).isNotEqualTo("safe");
    }

    @Test
    void preservaCamposFixosEMdcAllowList() throws Exception {
        String correlacao = UUID.randomUUID().toString();
        String traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
        String saga = UUID.randomUUID().toString();
        String os = UUID.randomUUID().toString();
        MDC.put("event_name", "evento_fixo");
        MDC.put("error_code", "CODIGO_FIXO");
        MDC.put("correlation_id", correlacao);
        MDC.put("traceparent", traceparent);
        MDC.put("saga_id", saga);
        MDC.put("os_id", os);
        MDC.put("campo_fora_da_allow_list", "nao-deve-sair");
        try {
            JsonNode json = emitir(false, "mensagem estavel");

            assertThat(json.get("service").asText()).isEqualTo("billing-service");
            assertThat(json.get("environment").asText()).isEqualTo("aws");
            assertThat(json.get("level").asText()).isEqualTo("WARN");
            assertThat(json.get("timestamp").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3,9}Z");
            assertThat(json.get("event_name").asText()).isEqualTo("evento_fixo");
            assertThat(json.get("error_code").asText()).isEqualTo("CODIGO_FIXO");
            assertThat(json.get("correlation_id").asText()).isEqualTo(correlacao);
            assertThat(json.get("traceparent").asText()).isEqualTo(traceparent);
            assertThat(json.get("saga_id").asText()).isEqualTo(saga);
            assertThat(json.get("os_id").asText()).isEqualTo(os);
            assertThat(json.has("campo_fora_da_allow_list")).isFalse();
        } finally {
            MDC.clear();
        }
    }

    @Test
    void mascaraSegredoEPiiDentroDaMensagem() throws Exception {
        String[] sentinelas = {
                "Bearer sentinelaFicticia0123456789abc",
                "eyJzZW50aW5lbGE.ZmljdGljaWE.c2VtYXNzaW5hdHVyYQ",
                "password=sentinela-senha-ficticia",
                "senha: sentinela-senha-ficticia",
                "client_secret=sentinela-cliente-ficticio",
                "checkout_url=https://sentinela.invalid/pagar/abc",
                "api_key=sentinela-chave-ficticia",
                "token=sentinela-token-ficticio",
                "APP_USR-0000000000000000-000000-sentinelaficticia00-000000000",
                "titular.ficticio@exemplo.invalid",
                "111.444.777-35",
                "11144477735",
                "(11) 91234-5678",
                "+55 11 91234-5678",
                "Authorization: Basic c2VudGluZWxhOmZpY3RpY2lh",
                "id_token=sentinela-id-ficticio",
                "DB_PASSWORD=sentinela-banco-ficticio",
                "jwt_secret=sentinela-jwt-ficticio",
                "secretKey=sentinela-chave-ficticia",
                "access_token=sentinela-acesso-ficticio",
                "AKIAABCDEFGHIJKLMNOP",
        };
        for (String sentinela : sentinelas) {
            JsonNode json = emitir(false, "antes " + sentinela + " depois");
            String texto = json.get("message").asText();

            assertThat(texto).as(sentinela).startsWith("antes ").endsWith(" depois").contains(MASCARA);
            assertThat(texto).as(sentinela).doesNotContain("sentinela").doesNotContain("titular").doesNotContain("1234")
                    .doesNotContain("111.444").doesNotContain("11144477735").doesNotContain("eyJ");
        }
    }

    @Test
    void mascaraValorEntreAspasComEspacoSemDeixarCauda() throws Exception {
        String texto = emitir(false, "antes password=\"sentinela a b\" e senha='sentinela c d' depois").get("message").asText();

        assertThat(texto).isEqualTo("antes " + MASCARA + " e " + MASCARA + " depois");
    }

    @Test
    void silenciaMensagensDoSqlExceptionHelperQueEcoamValoresDeRestricao() throws Exception {
        String saida = emitirLinha("org.hibernate.engine.jdbc.spi.SqlExceptionHelper", false,
                "Detail: Key (placa)=(SENT1234) already exists.");

        assertThat(saida).isEmpty();
    }

    @Test
    void incluiNomeDoLoggerParaDiagnostico() throws Exception {
        assertThat(emitir(false, "mensagem estavel").get("logger_name").asText()).isEqualTo("teste.redacao");
    }

    @Test
    void mascaraEntradaGrandeComPrefixosDeSegredoEmTempoLimitado() {
        String hostil = "a_".repeat(10_000) + "password " + "token=".repeat(3_000) + "Bearer " + "x".repeat(20_000);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            JsonNode json = emitir(false, hostil);
            assertThat(json.get("message").asText()).contains(MASCARA);
        });
    }

    @Test
    void mascaraCamposEstruturadosPorCaminho() throws Exception {
        String[] campos = {"authorization", "Authorization", "cookie", "password", "senha", "secret", "segredo", "token",
                "api_key", "apiKey", "client_secret", "checkout_url", "checkoutUrl", "cpf", "email", "phone", "telefone"};
        for (String campo : campos) {
            JsonNode json = emitir(true, "mensagem estavel", kv(campo, "valor-ficticio"));

            assertThat(json.get(campo).asText()).as(campo).isEqualTo(MASCARA);
        }
    }

    @Test
    void naoMascaraIdentificadoresLegitimosNemTextoComum() throws Exception {
        String uuid = UUID.randomUUID().toString();
        String traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
        String texto = "Pagamento " + uuid + " tratado em 2026-11-06T12:00:00.123Z trace " + traceparent;
        MDC.put("correlation_id", uuid);
        MDC.put("traceparent", traceparent);
        try {
            JsonNode json = emitir(false, texto);

            assertThat(json.get("message").asText()).isEqualTo(texto);
            assertThat(json.get("correlation_id").asText()).isEqualTo(uuid);
            assertThat(json.get("traceparent").asText()).isEqualTo(traceparent);
        } finally {
            MDC.clear();
        }
    }

    @Test
    void mascaraEntradaGrandeSemDegradarDesempenho() {
        String hostil = "a".repeat(20_000) + "@" + "b".repeat(20_000) + " " + "token=".repeat(3_000) + "1".repeat(20_000);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            JsonNode json = emitir(false, hostil);
            assertThat(json.get("message").asText()).contains(MASCARA);
        });
    }

    @Test
    void jsonEhValidoComCaracteresEspeciais() throws Exception {
        String texto = "aspas \" barra \\ quebra\nlinha acentuação ção";

        assertThat(emitir(false, texto).get("message").asText()).isEqualTo(texto);
    }

    /** {@code comArguments} acrescenta o provider {@code <arguments/>} ao XML real só para provar a máscara por caminho. */
    private static JsonNode emitir(boolean comArguments, String formato, Object... args) throws Exception {
        String linha = emitirLinha("teste.redacao", comArguments, formato, args);
        assertThat(linha.lines().count()).as("uma linha JSON por evento").isEqualTo(1);
        return JSON.readTree(linha);
    }

    private static String emitirLinha(String nomeLogger, boolean comArguments, String formato, Object... args) throws Exception {
        String xml;
        try (InputStream in = LogbackJsonRedacaoTest.class.getResourceAsStream("/logback-spring.xml")) {
            assertThat(in).as("logback-spring.xml no classpath").isNotNull();
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (comArguments) xml = xml.replace("<message/>", "<message/><arguments/>");

        PrintStream original = System.out;
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        LoggerContext contexto = new LoggerContext();
        contexto.setMDCAdapter(MDC.getMDCAdapter());
        try {
            System.setOut(new PrintStream(saida, true, StandardCharsets.UTF_8));
            contexto.putProperty("OFICINA_ENVIRONMENT", "aws");
            JoranConfigurator configurador = new JoranConfigurator();
            configurador.setContext(contexto);
            configurador.doConfigure(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Logger logger = contexto.getLogger(nomeLogger);
            logger.warn(formato, args);
        } finally {
            contexto.stop();
            System.setOut(original);
        }
        return saida.toString(StandardCharsets.UTF_8).strip();
    }
}
