package com.oficina.billing.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import static org.assertj.core.api.Assertions.assertThat;

/** O perfil `aws` monta o cliente SQS pela default chain; sem ele, o caminho local/LocalStack é preservado. */
class MensageriaConfigurationPerfilTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MensageriaClientesConfiguration.class)
            .withPropertyValues("billing.mensageria.hosts-permitidos=127.0.0.1");

    @Test void perfilAwsUsaDefaultChainSemEndpointLocal() {
        runner.withPropertyValues("spring.profiles.active=aws").withBean(BillingProperties.class,
                        () -> new BillingProperties(null, null, new BillingProperties.Mensageria(true, "us-east-1", "", "c", "r")))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    var config = ctx.getBean(ClientesSqs.class).envio().serviceClientConfiguration();
                    assertThat(config.credentialsProvider()).isInstanceOf(DefaultCredentialsProvider.class);
                    assertThat(config.endpointOverride()).isEmpty();
                });
    }

    @Test void perfilAwsFalhaSeEndpointForConfigurado() {
        runner.withPropertyValues("spring.profiles.active=aws").withBean(BillingProperties.class,
                        () -> new BillingProperties(null, null, new BillingProperties.Mensageria(true, "us-east-1", "http://127.0.0.1:4566", "c", "r")))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test void perfilLocalPreservaLocalStackComCredenciaisDummy() {
        runner.withPropertyValues("spring.profiles.active=local").withBean(BillingProperties.class,
                        () -> new BillingProperties(null, null, new BillingProperties.Mensageria(true, "us-east-1", "http://127.0.0.1:4566", "c", "r")))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    var config = ctx.getBean(ClientesSqs.class).envio().serviceClientConfiguration();
                    assertThat(config.credentialsProvider()).isInstanceOf(StaticCredentialsProvider.class);
                    assertThat(config.endpointOverride()).isPresent();
                });
    }

    @Test void perfisAwsELocalJuntosFalham() {
        runner.withPropertyValues("spring.profiles.active=aws,local").withBean(BillingProperties.class,
                        () -> new BillingProperties(null, null, new BillingProperties.Mensageria(true, "us-east-1", "", "c", "r")))
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
