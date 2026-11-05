package com.oficina.billing.config;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
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
import java.util.function.UnaryOperator;

/**
 * Owns every SQS client. {@link #criar} is local-only (control S-3): the endpoint must be an approved LocalStack host
 * and the credentials are the fixed dummy pair. {@link #criarAws} is the `aws` profile: no endpoint override and no
 * static credentials, so the AWS SDK default credentials provider chain resolves the identity.
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
        AwsCredentialsProvider credenciais = StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        return montar(http, Region.of(m.region()), credenciais, endpoint);
    }

    /** Perfil `aws`: credenciais pela default chain do SDK; endpoint local e endpoints ambientais (env/system properties do SDK) são proibidos. */
    public static ClientesSqs criarAws(BillingProperties.Mensageria m) {
        return criarAws(m, System::getenv, System::getProperty);
    }

    static ClientesSqs criarAws(BillingProperties.Mensageria m, UnaryOperator<String> variaveis,
                                UnaryOperator<String> propriedades) {
        EndpointsAmbientaisAws.exigirAusentes(variaveis, propriedades);
        if (m.endpoint() != null && !m.endpoint().isBlank()) {
            throw new IllegalStateException("Perfil aws não aceita endpoint de mensageria customizado");
        }
        SdkHttpClient http = UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(1))
                .socketTimeout(Duration.ofSeconds(15)).build();
        return montar(http, Region.of(m.region()), DefaultCredentialsProvider.create(), null);
    }

    private static ClientesSqs montar(SdkHttpClient http, Region region, AwsCredentialsProvider credenciais, URI endpoint) {
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
        var builder = SqsClient.builder().region(region).credentialsProvider(credenciais).httpClient(http)
                .overrideConfiguration(limites);
        if (endpoint != null) {
            builder.endpointOverride(endpoint);
        }
        return builder.build();
    }

    public SqsClient envio() { return envio; }

    public SqsClient recepcao() { return recepcao; }

    @Override public void close() {
        IoUtils.closeQuietly(recepcao, null);
        IoUtils.closeQuietly(envio, null);
        IoUtils.closeQuietly(http, null);
    }
}
