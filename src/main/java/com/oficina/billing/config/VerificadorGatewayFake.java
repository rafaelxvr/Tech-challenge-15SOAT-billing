package com.oficina.billing.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Control S-5: the fake gateway and a real Mercado Pago credential must never coexist in one process. */
@Component
@Profile("gateway-fake")
public class VerificadorGatewayFake {
    public VerificadorGatewayFake(BillingProperties properties) {
        String token = properties.mercadopago() == null ? null : properties.mercadopago().accessToken();
        if (token != null && !token.isBlank()) {
            throw new IllegalStateException("Perfil gateway-fake não pode coexistir com credencial real do Mercado Pago");
        }
    }
}
