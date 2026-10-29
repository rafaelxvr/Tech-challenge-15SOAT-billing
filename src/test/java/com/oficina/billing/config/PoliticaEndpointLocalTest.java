package com.oficina.billing.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Control S-3: negative tests for hosts outside the explicit local allowlist. */
class PoliticaEndpointLocalTest {
    static final Set<String> PERMITIDOS = Set.of("localstack.infra.svc", "localstack.infra.svc.cluster.local", "127.0.0.1");

    @ParameterizedTest
    @ValueSource(strings = {"http://localstack.infra.svc:4566", "http://localstack.infra.svc.cluster.local:4566",
            "http://127.0.0.1:4566", "http://LOCALSTACK.infra.svc:4566"})
    void hostsDaAllowlistSaoAceitos(String endpoint) {
        assertThat(PoliticaEndpointLocal.exigir(endpoint, PERMITIDOS)).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "https://sqs.us-east-1.amazonaws.com", "http://sqs.us-east-1.amazonaws.com.localstack.infra.svc.evil.com",
            "http://localstack.infra.svc@sqs.us-east-1.amazonaws.com", "ftp://127.0.0.1:4566", "http://10.0.0.5:4566",
            "http://localhost:4566", "nao é uma uri", "localstack.infra.svc:4566"})
    void enderecoVazioOuForaDaAllowlistFalha(String endpoint) {
        assertThatThrownBy(() -> PoliticaEndpointLocal.exigir(endpoint, PERMITIDOS)).isInstanceOf(IllegalStateException.class);
    }

    @Test void mensagemDeErroNaoVazaOEndereco() {
        assertThatThrownBy(() -> PoliticaEndpointLocal.exigir("https://segredo.amazonaws.com", PERMITIDOS))
                .hasMessageNotContaining("amazonaws");
    }
}
