package com.oficina.billing.config;

import com.oficina.billing.adapter.in.messaging.SqsComandosListener;
import com.oficina.billing.adapter.out.messaging.BackoffPublicacao;
import com.oficina.billing.adapter.out.messaging.OutboxPublisher;
import com.oficina.billing.adapter.out.messaging.SqsEnviadorMensagens;
import com.oficina.billing.application.ExpiracaoPagamentosService;
import com.oficina.billing.application.ProcessadorComandos;
import com.oficina.billing.application.port.EnviadorMensagens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "billing.mensageria.enabled", havingValue = "true")
public class MensageriaConfiguration {
    @Bean(destroyMethod = "close") ClientesSqs clientesSqs(BillingProperties p,
            @Value("${billing.mensageria.hosts-permitidos}") List<String> hostsPermitidos) {
        return ClientesSqs.criar(p.mensageria(), Set.copyOf(hostsPermitidos));
    }

    @Bean EnviadorMensagens enviadorMensagens(ClientesSqs clientes, BillingProperties p) {
        return new SqsEnviadorMensagens(clientes.envio(), p.mensageria().filaRespostas());
    }

    @Bean OutboxPublisher outboxPublisher(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                          EnviadorMensagens enviador, Clock clock) {
        return new OutboxPublisher(jdbc, new TransactionTemplate(manager), enviador, clock, BackoffPublicacao.aleatorio());
    }

    @Bean PollerPeriodico outboxPoller(OutboxPublisher publisher) {
        return new PollerPeriodico("billing-outbox", () -> {
            for (int i = 0; i < 20 && publisher.publicarProximo(); i++) { /* drain up to 20 per tick */ }
        }, Duration.ofSeconds(1));
    }

    @Bean PollerPeriodico expiracaoPoller(ExpiracaoPagamentosService expiracao) {
        return new PollerPeriodico("billing-expiracao", () -> expiracao.expirarVencidos(20), Duration.ofSeconds(15));
    }

    @Bean SqsComandosListener sqsComandosListener(ClientesSqs clientes, BillingProperties p, ProcessadorComandos processador) {
        SqsEnviadorMensagens.validarFila(p.mensageria().filaComandos());
        return new SqsComandosListener(clientes.recepcao(), p.mensageria().filaComandos(), processador);
    }
}
