package com.oficina.billing.config;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.utils.IoUtils;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

/**
 * Owns every SQS client. Local-only (control S-3): the endpoint must be an approved LocalStack host and the
 * credentials are the fixed dummy pair, so the default AWS credentials chain is never consulted.
 */
public final class ClientesSqs implements AutoCloseable {
    private final SdkHttpClient http;
    private final SqsClient envio;
    private final SqsClient recepcao;

    private ClientesSqs(SdkHttpClient http, SqsClient envio, SqsClient recepcao) {
        this.http = http;
        this.envio = envio;
        this.recepcao = recepcao;
    }

    public static ClientesSqs criar(BillingProperties.Mensageria m, Set<String> hostsPermitidos) {
        URI endpoint = PoliticaEndpointLocal.exigir(m.endpoint(), hostsPermitidos);
        SdkHttpClient http = UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(1))
                .socketTimeout(Duration.ofSeconds(15)).build();
        Region region = Region.of(m.region());
        AwsCredentialsProvider credenciais = StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        SqsClient envio = sqs(region, credenciais, http, endpoint, limites(Duration.ofSeconds(2)));
        SqsClient recepcao = sqs(region, credenciais, http, endpoint, limites(Duration.ofSeconds(15)));
        return new ClientesSqs(http, envio, recepcao);
    }

    private static ClientOverrideConfiguration limites(Duration timeout) {
        return ClientOverrideConfiguration.builder().apiCallTimeout(timeout).apiCallAttemptTimeout(timeout)
                .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()).build();
    }

    private static SqsClient sqs(Region region, AwsCredentialsProvider credenciais, SdkHttpClient http, URI endpoint,
                                 ClientOverrideConfiguration limites) {
        return SqsClient.builder().region(region).credentialsProvider(credenciais).httpClient(http)
                .overrideConfiguration(limites).endpointOverride(endpoint).build();
    }

    public SqsClient envio() { return envio; }

    public SqsClient recepcao() { return recepcao; }

    @Override public void close() {
        IoUtils.closeQuietly(recepcao, null);
        IoUtils.closeQuietly(envio, null);
        IoUtils.closeQuietly(http, null);
    }
}
