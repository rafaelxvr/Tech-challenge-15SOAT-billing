package com.oficina.billing.mensagem;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oficina.billing.domain.Orcamento;
import com.oficina.billing.domain.Pagamento;

import java.util.UUID;

/** Single place that shapes reply payloads, so the producer contract test covers what use cases emit. */
public final class PayloadsResposta {
    private PayloadsResposta() {}

    private static ObjectNode novo() { return JsonNodeFactory.instance.objectNode(); }

    public static ObjectNode orcamentoGerado(Orcamento o) {
        return novo().put("orcamentoId", o.getId().toString()).put("total", o.getTotal());
    }

    public static ObjectNode orcamentoFalhou() { return novo().put("motivo", "ITENS_INVALIDOS"); }

    public static ObjectNode orcamentoAprovado(Orcamento o, Pagamento p) {
        return novo().put("orcamentoId", o.getId().toString()).put("pagamentoId", p.getId().toString())
                .put("checkoutUrl", p.getCheckoutUrl());
    }

    public static ObjectNode orcamentoRecusado(Orcamento o) { return novo().put("orcamentoId", o.getId().toString()); }

    public static ObjectNode pagamento(Pagamento p) { return novo().put("pagamentoId", p.getId().toString()); }

    public static ObjectNode orcamentoCancelado(UUID orcamentoId) { return idOuNulo("orcamentoId", orcamentoId); }

    public static ObjectNode pagamentoEstornado(UUID pagamentoId) { return idOuNulo("pagamentoId", pagamentoId); }

    public static ObjectNode compensacaoFalhou(String compensacao) { return novo().put("compensacao", compensacao); }

    private static ObjectNode idOuNulo(String campo, UUID id) {
        ObjectNode node = novo();
        if (id == null) node.putNull(campo); else node.put(campo, id.toString());
        return node;
    }
}
