package com.oficina.billing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.List;
import java.util.Set;

/**
 * Importada só por {@link MensageriaConfiguration} (sem @Configuration, para o component scan não a carregar com a
 * mensageria desligada). Escolhe o cliente SQS: perfil `aws` usa a default credentials chain; os demais perfis usam LocalStack.
 */
public class MensageriaClientesConfiguration {
    @Bean(destroyMethod = "close") @Profile("!aws") ClientesSqs clientesSqsLocal(BillingProperties p,
            @Value("${billing.mensageria.hosts-permitidos}") List<String> hostsPermitidos) {
        return ClientesSqs.criar(p.mensageria(), Set.copyOf(hostsPermitidos));
    }

    @Bean(destroyMethod = "close") @Profile("aws") ClientesSqs clientesSqsAws(BillingProperties p, Environment ambiente) {
        if (ambiente.acceptsProfiles(Profiles.of("local", "kind"))) {
            throw new IllegalStateException("Perfil aws não pode ser combinado com local ou kind");
        }
        return ClientesSqs.criarAws(p.mensageria());
    }
}
