package com.oficina.billing.support;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oficina.billing.mensagem.Envelope;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public final class Comandos {
    public static final Instant T0 = Instant.parse("2026-09-15T12:00:00Z");
    public static final UUID CLIENTE = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private Comandos() {}

    public static UUID sagaDe(UUID osId) {
        return UUID.nameUUIDFromBytes(("saga-" + osId).getBytes(StandardCharsets.UTF_8));
    }

    public static Envelope comando(String tipo, UUID osId, int passo, ObjectNode payload) {
        return new Envelope(UUID.randomUUID(), Envelope.Kind.COMMAND, tipo, 1, sagaDe(osId), osId, passo, null, T0,
                UUID.randomUUID().toString(), "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01", payload);
    }

    public static ObjectNode gerarOrcamento(int versao) {
        ObjectNode p = JsonNodeFactory.instance.objectNode().put("numero", 42).put("clienteId", CLIENTE.toString())
                .put("versaoOrcamento", versao);
        p.putArray("itens")
                .add(item("SERVICO", "Troca de óleo", 1, "80.00"))
                .add(item("PECA", "Filtro", 2, "35.50"));
        return p;
    }

    public static ObjectNode item(String tipo, String descricao, int quantidade, String preco) {
        return JsonNodeFactory.instance.objectNode().put("tipo", tipo).put("referenciaId", UUID.randomUUID().toString())
                .put("descricao", descricao).put("quantidade", quantidade).put("precoUnitario", new BigDecimal(preco));
    }

    public static ObjectNode decisao(int versao, String decisao) {
        return JsonNodeFactory.instance.objectNode().put("versaoOrcamento", versao).put("decisao", decisao);
    }

    public static ObjectNode compensacao(int versao, String motivo) {
        return JsonNodeFactory.instance.objectNode().put("versaoOrcamento", versao).put("motivo", motivo);
    }
}
