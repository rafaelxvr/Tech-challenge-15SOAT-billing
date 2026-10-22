package com.oficina.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "billing")
public record BillingProperties(Pagamento pagamento, MercadoPago mercadopago, Mensageria mensageria) {
    public record Pagamento(Duration validade) {}

    public record MercadoPago(String baseUrl, String accessToken, String webhookSecret, String notificationUrl,
                              boolean sandbox, Duration toleranciaAssinatura) {}

    public record Mensageria(boolean enabled, String region, String endpoint, String filaComandos, String filaRespostas) {}
}
