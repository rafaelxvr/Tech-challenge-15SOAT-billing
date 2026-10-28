package com.oficina.billing.adapter.in.rest;

import com.oficina.billing.support.TokensTeste;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP real (servlet container): MockMvc não redespacha para /error, então só aqui o 403 vindo de
 * sendError() seria reautorizado sem credencial e viraria 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "gateway-fake"})
class SegurancaHttpRealTest {
    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String bearer) throws Exception {
        var url = "http://localhost:" + port + "/billing/orcamentos?osId=" + UUID.randomUUID();
        var request = HttpRequest.newBuilder(URI.create(url)).GET();
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void mecanicoEmRotaSoAdminRecebe403() throws Exception {
        assertThat(get(TokensTeste.staff("MECANICO")).statusCode()).isEqualTo(403);
    }

    @Test void semCredencialContinuaRecebendo401() throws Exception {
        assertThat(get(null).statusCode()).isEqualTo(401);
    }

    @Test void adminRecebe200() throws Exception {
        assertThat(get(TokensTeste.staff("ADMIN")).statusCode()).isEqualTo(200);
    }
}
