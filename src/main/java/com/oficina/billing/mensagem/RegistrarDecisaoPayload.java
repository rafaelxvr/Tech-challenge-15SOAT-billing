package com.oficina.billing.mensagem;

public record RegistrarDecisaoPayload(int versaoOrcamento, String decisao) {
    public RegistrarDecisaoPayload {
        if (versaoOrcamento < 1 || !("APROVADO".equals(decisao) || "RECUSADO".equals(decisao))) {
            throw new IllegalArgumentException("RegistrarDecisao inválido");
        }
    }

    public boolean aprovado() { return "APROVADO".equals(decisao); }
}
