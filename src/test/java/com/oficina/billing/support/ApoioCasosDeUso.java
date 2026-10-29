package com.oficina.billing.support;

import com.oficina.billing.adapter.out.fake.GatewayPagamentoFake;
import com.oficina.billing.application.PoliticaPagamento;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

@TestConfiguration(proxyBeanMethods = false)
public class ApoioCasosDeUso {
    @Bean RelogioAjustavel clock() { return new RelogioAjustavel(Comandos.T0); }

    @Bean PoliticaPagamento politicaPagamento() { return new PoliticaPagamento(Duration.ofMinutes(30)); }

    @Bean com.oficina.billing.application.TentativasGateway tentativasGateway() {
        return new com.oficina.billing.application.TentativasGateway(3, Duration.ofSeconds(1), millis -> { });
    }

    @Bean GatewayPagamentoFake gatewayPagamento() { return new GatewayPagamentoFake(); }
}
