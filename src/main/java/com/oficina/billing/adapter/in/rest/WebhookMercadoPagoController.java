package com.oficina.billing.adapter.in.rest;

import com.oficina.billing.adapter.out.mercadopago.AssinaturaWebhook;
import com.oficina.billing.application.ConfirmacaoPagamentoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Public; trusted only after the HMAC check, and the payment is always re-read from the provider. */
@RestController
@RequiredArgsConstructor
public class WebhookMercadoPagoController {
    private static final Logger LOG = LoggerFactory.getLogger(WebhookMercadoPagoController.class);
    private final AssinaturaWebhook assinatura;
    private final ConfirmacaoPagamentoService confirmacao;

    @PostMapping("/webhooks/mercadopago")
    public ResponseEntity<Void> receber(@RequestHeader(value = "x-signature", required = false) String xSignature,
                                        @RequestHeader(value = "x-request-id", required = false) String xRequestId,
                                        @RequestParam(value = "data.id", required = false) String dataIdQuery,
                                        @RequestParam(value = "type", required = false) String tipoQuery,
                                        @RequestBody(required = false) Notificacao corpo) {
        String dataId = dataIdQuery != null ? dataIdQuery : corpo == null || corpo.data() == null ? null : corpo.data().id();
        String tipo = tipoQuery != null ? tipoQuery : corpo == null ? null : corpo.type();
        if (!assinatura.valida(xSignature, xRequestId, dataId)) {
            // Counted by the "Webhooks Mercado Pago rejeitados" widget (Plan 4 Task 5, decision D14); no header values logged.
            MDC.put("event_name", "mercadopago_webhook_rejeitado");
            MDC.put("error_code", "WEBHOOK_SIGNATURE_INVALID");
            try { LOG.warn("safe"); } finally { MDC.remove("event_name"); MDC.remove("error_code"); }
            return ResponseEntity.status(401).build();
        }
        if ("payment".equals(tipo)) confirmacao.processarNotificacao(dataId);
        return ResponseEntity.ok().build();
    }

    public record Notificacao(String type, Dados data) {
        public record Dados(String id) {}
    }
}
