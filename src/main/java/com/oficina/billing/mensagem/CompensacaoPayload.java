package com.oficina.billing.mensagem;

public record CompensacaoPayload(int versaoOrcamento, String motivo) {
    public CompensacaoPayload {
        if (versaoOrcamento < 1 || motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Compensação inválida");
        }
    }
}
