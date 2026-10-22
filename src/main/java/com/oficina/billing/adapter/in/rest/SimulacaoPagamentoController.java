package com.oficina.billing.adapter.in.rest;

import com.oficina.billing.adapter.out.fake.GatewayPagamentoFake;
import com.oficina.billing.application.ConfirmacaoPagamentoService;
import com.oficina.billing.application.port.GatewayPagamento.StatusExterno;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/** Demo/BDD only (profile gateway-fake): drives the same path as a verified webhook. */
@RestController
@Profile("gateway-fake")
@RequiredArgsConstructor
public class SimulacaoPagamentoController {
    private final GatewayPagamentoFake gateway;
    private final ConfirmacaoPagamentoService confirmacao;

    @PostMapping("/simulacoes/pagamentos/{pagamentoId}")
    public Map<String, String> simular(@PathVariable UUID pagamentoId, @RequestBody Simulacao simulacao) {
        StatusExterno status = switch (simulacao.status() == null ? "" : simulacao.status()) {
            case "approved" -> StatusExterno.APROVADO;
            case "rejected" -> StatusExterno.RECUSADO;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
        String mpPaymentId = gateway.simular(pagamentoId, status);
        confirmacao.processarNotificacao(mpPaymentId);
        return Map.of("mpPaymentId", mpPaymentId);
    }

    public record Simulacao(String status) {}
}
