package com.oficina.billing.mensagem;

public enum TipoResposta {
    ORCAMENTO_GERADO("OrcamentoGerado"),
    ORCAMENTO_FALHOU("OrcamentoFalhou"),
    ORCAMENTO_APROVADO("OrcamentoAprovado"),
    ORCAMENTO_RECUSADO("OrcamentoRecusado"),
    PAGAMENTO_CONFIRMADO("PagamentoConfirmado"),
    PAGAMENTO_RECUSADO("PagamentoRecusado"),
    PAGAMENTO_EXPIRADO("PagamentoExpirado"),
    ORCAMENTO_CANCELADO("OrcamentoCancelado"),
    PAGAMENTO_ESTORNADO("PagamentoEstornado"),
    COMPENSACAO_FALHOU("CompensacaoFalhou");

    private final String tipo;
    TipoResposta(String tipo) { this.tipo = tipo; }
    public String tipo() { return tipo; }
}
