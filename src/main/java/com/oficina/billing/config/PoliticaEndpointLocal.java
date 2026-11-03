package com.oficina.billing.config;

import java.net.URI;
import java.util.Set;

/** Control S-3: the messaging endpoint must be an explicitly approved local host; anything else fails fast. */
public final class PoliticaEndpointLocal {
    private PoliticaEndpointLocal() {}

    public static URI exigir(String endpoint, Set<String> hostsPermitidos) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("Endpoint de mensageria local é obrigatório");
        }
        URI uri;
        try {
            uri = URI.create(endpoint.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Endpoint de mensageria inválido");
        }
        boolean esquemaValido = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        String host = uri.getHost();
        if (!esquemaValido || uri.getUserInfo() != null || host == null || !hostsPermitidos.contains(host.toLowerCase())) {
            throw new IllegalStateException("Endpoint de mensageria fora da allowlist local");
        }
        return uri;
    }
}
