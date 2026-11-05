package com.oficina.billing.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Diferencia o cliente SQS local (LocalStack, test/test) do perfil aws (default credentials chain, sem endpoint). */
class ClientesSqsTest {
    static final Set<String> HOSTS = Set.of("127.0.0.1");

    static BillingProperties.Mensageria mensageria(String endpoint) {
        return new BillingProperties.Mensageria(true, "us-east-1", endpoint, "fila-c", "fila-r");
    }

    @Test void clienteLocalUsaEndpointELocalStackECredenciaisDummy() {
        try (ClientesSqs clientes = ClientesSqs.criar(mensageria("http://127.0.0.1:4566"), HOSTS)) {
            for (SqsClient sqs : new SqsClient[] {clientes.envio(), clientes.recepcao()}) {
                var config = sqs.serviceClientConfiguration();
                assertThat(config.endpointOverride()).isPresent();
                assertThat(config.endpointOverride().get().getHost()).isEqualTo("127.0.0.1");
                assertThat(config.credentialsProvider()).isInstanceOf(StaticCredentialsProvider.class);
                var credenciais = config.credentialsProvider().resolveIdentity().join();
                assertThat(credenciais.accessKeyId()).isEqualTo("test");
            }
        }
    }

    @Test void clienteAwsUsaDefaultCredentialsChainSemEndpoint() {
        try (ClientesSqs clientes = ClientesSqs.criarAws(mensageria(""))) {
            for (SqsClient sqs : new SqsClient[] {clientes.envio(), clientes.recepcao()}) {
                var config = sqs.serviceClientConfiguration();
                assertThat(config.endpointOverride()).isEmpty();
                assertThat(config.credentialsProvider()).isInstanceOf(DefaultCredentialsProvider.class);
                assertThat(config.region().id()).isEqualTo("us-east-1");
            }
        }
    }

    @Test void clienteAwsRecusaEndpointConfigurado() {
        assertThatThrownBy(() -> ClientesSqs.criarAws(mensageria("http://127.0.0.1:4566")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("endpoint");
    }
}
