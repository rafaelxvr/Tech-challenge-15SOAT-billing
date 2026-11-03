package com.oficina.billing.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Control S-3: local/Kind startup checks for endpoint, dummy credentials and absence of AWS identity. */
class VerificadorAmbienteLocalTest {
    static final List<String> HOSTS = List.of("localstack.infra.svc", "127.0.0.1");

    static BillingProperties props(String endpoint) {
        return new BillingProperties(null, null, new BillingProperties.Mensageria(false, "us-east-1", endpoint, "c", "r"));
    }

    static MockEnvironment credenciaisDummy() {
        return new MockEnvironment().withProperty("AWS_ACCESS_KEY_ID", "test").withProperty("AWS_SECRET_ACCESS_KEY", "test");
    }

    @Test void configuracaoLocalValidaInicia() {
        assertThatCode(() -> new VerificadorAmbienteLocal(props("http://localstack.infra.svc:4566"), credenciaisDummy(), HOSTS))
                .doesNotThrowAnyException();
    }

    @Test void endpointVazioOuForaDaAllowlistFalha() {
        assertThatThrownBy(() -> new VerificadorAmbienteLocal(props(""), credenciaisDummy(), HOSTS))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new VerificadorAmbienteLocal(props("https://sqs.us-east-1.amazonaws.com"), credenciaisDummy(), HOSTS))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void credenciaisDummyAusentesOuDiferentesFalham() {
        assertThatThrownBy(() -> new VerificadorAmbienteLocal(props("http://127.0.0.1:4566"), new MockEnvironment(), HOSTS))
                .isInstanceOf(IllegalStateException.class);
        var real = new MockEnvironment().withProperty("AWS_ACCESS_KEY_ID", "AKIAEXEMPLO").withProperty("AWS_SECRET_ACCESS_KEY", "test");
        assertThatThrownBy(() -> new VerificadorAmbienteLocal(props("http://127.0.0.1:4566"), real, HOSTS))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void identidadeAwsPresenteFalha() {
        for (String variavel : VerificadorAmbienteLocal.IDENTIDADES_PROIBIDAS) {
            var ambiente = credenciaisDummy().withProperty(variavel, "valor");
            assertThatThrownBy(() -> new VerificadorAmbienteLocal(props("http://127.0.0.1:4566"), ambiente, HOSTS))
                    .as(variavel).isInstanceOf(IllegalStateException.class);
        }
    }
}
