package com.oficina.billing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Control S-3: local/Kind profiles refuse to start with an empty or non-allowlisted endpoint, without the dummy
 * test/test credentials, or with any AWS identity variable present.
 */
@Component
@Profile({"local", "kind"})
public class VerificadorAmbienteLocal {
    static final List<String> IDENTIDADES_PROIBIDAS = List.of("AWS_PROFILE", "AWS_ROLE_ARN", "AWS_WEB_IDENTITY_TOKEN_FILE",
            "AWS_SESSION_TOKEN");

    public VerificadorAmbienteLocal(BillingProperties properties, Environment ambiente,
                                    @Value("${billing.mensageria.hosts-permitidos}") List<String> hostsPermitidos) {
        PoliticaEndpointLocal.exigir(properties.mensageria() == null ? null : properties.mensageria().endpoint(),
                Set.copyOf(hostsPermitidos));
        if (!"test".equals(ambiente.getProperty("AWS_ACCESS_KEY_ID"))
                || !"test".equals(ambiente.getProperty("AWS_SECRET_ACCESS_KEY"))) {
            throw new IllegalStateException("Credenciais dummy test/test são obrigatórias nos perfis local e kind");
        }
        for (String variavel : IDENTIDADES_PROIBIDAS) {
            String valor = ambiente.getProperty(variavel);
            if (valor != null && !valor.isBlank()) {
                throw new IllegalStateException("Identidade AWS não é permitida nos perfis local e kind");
            }
        }
    }
}
