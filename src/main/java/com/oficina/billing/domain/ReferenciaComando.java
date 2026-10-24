package com.oficina.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/** What a later reply needs from the command it answers (saga correlation and trace). */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferenciaComando {
    @Column(name = "ref_saga_id", nullable = false) private UUID sagaId;
    @Column(name = "ref_os_id", nullable = false) private UUID osId;
    @Column(name = "ref_passo", nullable = false) private int passo;
    @Column(name = "ref_comando_id", nullable = false) private UUID comandoId;
    @Column(name = "ref_correlation_id", nullable = false, length = 36) private String correlationId;
    @Column(name = "ref_traceparent", length = 55) private String traceparent;

    public ReferenciaComando(UUID sagaId, UUID osId, int passo, UUID comandoId, String correlationId, String traceparent) {
        this.sagaId = Objects.requireNonNull(sagaId, "sagaId");
        this.osId = Objects.requireNonNull(osId, "osId");
        this.passo = passo;
        this.comandoId = Objects.requireNonNull(comandoId, "comandoId");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        this.traceparent = traceparent;
    }
}
