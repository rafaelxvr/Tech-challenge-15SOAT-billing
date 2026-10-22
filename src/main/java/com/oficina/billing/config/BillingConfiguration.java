package com.oficina.billing.config;

import com.oficina.billing.adapter.out.fake.GatewayPagamentoFake;
import com.oficina.billing.adapter.out.mercadopago.AssinaturaWebhook;
import com.oficina.billing.adapter.out.mercadopago.MercadoPagoGateway;
import com.oficina.billing.application.PoliticaPagamento;
import com.oficina.billing.application.TentativasGateway;
import com.oficina.billing.application.port.GatewayPagamento;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class BillingConfiguration {
    @Bean PoliticaPagamento politicaPagamento(BillingProperties p) { return new PoliticaPagamento(p.pagamento().validade()); }

    @Bean TentativasGateway tentativasGateway() {
        return new TentativasGateway(3, Duration.ofSeconds(1), millis -> {
            try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
    }

    @Bean AssinaturaWebhook assinaturaWebhook(BillingProperties p, Clock clock) {
        return new AssinaturaWebhook(p.mercadopago().webhookSecret(), p.mercadopago().toleranciaAssinatura(), clock);
    }

    @Bean @Profile("!gateway-fake")
    GatewayPagamento mercadoPagoGateway(RestClient.Builder builder, BillingProperties p) {
        return new MercadoPagoGateway(builder, p.mercadopago());
    }

    @Bean @Profile("gateway-fake")
    GatewayPagamentoFake gatewayPagamentoFake() { return new GatewayPagamentoFake(); }
}
