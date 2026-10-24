package com.oficina.billing.mensagem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.oficina.billing.domain.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Producer side: every reply billing can emit matches its canonical schema and the envelope schema. */
class ContratoRespostasTest {
    static final Instant T0 = Instant.parse("2026-09-15T12:00:00Z");
    final JsonSchemaFactory schemas = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    final ObjectMapper json = new ObjectMapper();
    final MapeadorMensagens mapeador = new MapeadorMensagens();
    final FabricaRespostas fabrica = new FabricaRespostas(Clock.fixed(T0, ZoneOffset.UTC));

    static ObjectNode payload(TipoResposta tipo) {
        var o = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 42, 1, List.of(
                new ItemOrcamento(TipoItem.PECA, UUID.randomUUID(), "Filtro", 2, Dinheiro.de("35.50"))), T0);
        o.aprovar(T0);
        var origem = new ReferenciaComando(UUID.randomUUID(), o.getOsId(), 3, UUID.randomUUID(), UUID.randomUUID().toString(), null);
        var p = Pagamento.abrir(UUID.randomUUID(), o, "pref", "https://mp.example/c", T0.plus(Duration.ofMinutes(30)), origem, T0);
        return switch (tipo) {
            case ORCAMENTO_GERADO -> PayloadsResposta.orcamentoGerado(o);
            case ORCAMENTO_FALHOU -> PayloadsResposta.orcamentoFalhou();
            case ORCAMENTO_APROVADO -> PayloadsResposta.orcamentoAprovado(o, p);
            case ORCAMENTO_RECUSADO -> PayloadsResposta.orcamentoRecusado(o);
            case PAGAMENTO_CONFIRMADO, PAGAMENTO_RECUSADO, PAGAMENTO_EXPIRADO -> PayloadsResposta.pagamento(p);
            case ORCAMENTO_CANCELADO -> PayloadsResposta.orcamentoCancelado(null);
            case PAGAMENTO_ESTORNADO -> PayloadsResposta.pagamentoEstornado(p.getId());
            case COMPENSACAO_FALHOU -> PayloadsResposta.compensacaoFalhou("EstornarPagamento");
        };
    }

    @ParameterizedTest
    @EnumSource(TipoResposta.class)
    void respostaRespeitaContratos(TipoResposta tipo) throws Exception {
        var origem = new ReferenciaComando(UUID.randomUUID(), UUID.randomUUID(), 3, UUID.randomUUID(),
                UUID.randomUUID().toString(), "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        var envelope = fabrica.responder(origem, tipo.tipo(), payload(tipo));
        JsonNode node = json.readTree(mapeador.escrever(envelope));
        assertThat(schemas.getSchema(Files.readString(Path.of("contracts", "envelope", "v1.json"))).validate(node)).isEmpty();
        assertThat(schemas.getSchema(Files.readString(Path.of("contracts", tipo.tipo(), "v1.json"))).validate(node.get("payload")))
                .isEmpty();
        assertThat(node.get("kind").asText()).isEqualTo("REPLY");
        assertThat(node.get("causationId").asText()).isEqualTo(origem.getComandoId().toString());
        assertThat(node.get("passo").asInt()).isEqualTo(3);
        assertThat(node.get("traceparent").asText()).isEqualTo(origem.getTraceparent());
    }
}
