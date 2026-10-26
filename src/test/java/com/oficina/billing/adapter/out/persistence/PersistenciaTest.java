package com.oficina.billing.adapter.out.persistence;

import com.oficina.billing.domain.*;
import com.oficina.billing.support.PostgresTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenciaTest extends PostgresTeste {
    static final Instant T0 = Instant.parse("2026-09-15T12:00:00Z");
    @Autowired OrcamentoRepository orcamentos;
    @Autowired PagamentoRepository pagamentos;

    @BeforeEach void limpar() {
        jdbc.update("DELETE FROM pagamento");
        jdbc.update("DELETE FROM orcamento_item");
        jdbc.update("DELETE FROM orcamento");
    }

    Orcamento novo(UUID osId, int versao) {
        return Orcamento.gerar(osId, UUID.randomUUID(), UUID.randomUUID(), 42, versao, List.of(
                new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Troca de óleo", 1, Dinheiro.de("80.00")),
                new ItemOrcamento(TipoItem.PECA, UUID.randomUUID(), "Filtro", 2, Dinheiro.de("35.50"))), T0);
    }

    @Test void orcamentoComItensIdaEVolta() {
        UUID os = UUID.randomUUID();
        transaction.executeWithoutResult(st -> orcamentos.save(novo(os, 1)));
        transaction.executeWithoutResult(st -> {
            var lido = orcamentos.findByOsIdAndVersao(os, 1).orElseThrow();
            assertThat(lido.getItens()).extracting(ItemOrcamento::getDescricao).containsExactly("Troca de óleo", "Filtro");
            assertThat(lido.valorTotal()).isEqualTo(Dinheiro.de("151.00"));
            assertThat(lido.getVersaoRegistro()).isZero();
        });
    }

    @Test void umOrcamentoPorOsEVersao() {
        UUID os = UUID.randomUUID();
        transaction.executeWithoutResult(st -> orcamentos.save(novo(os, 1)));
        assertThatThrownBy(() -> transaction.executeWithoutResult(st -> orcamentos.saveAndFlush(novo(os, 1))))
                .isInstanceOf(DataIntegrityViolationException.class);
        transaction.executeWithoutResult(st -> orcamentos.save(novo(os, 2)));
        assertThat(orcamentos.findByOsIdOrderByVersaoAsc(os)).extracting(Orcamento::getVersao).containsExactly(1, 2);
    }

    @Test void pagamentoComOrigemEPendentesVencidos() {
        UUID os = UUID.randomUUID();
        var origem = new ReferenciaComando(UUID.randomUUID(), os, 3, UUID.randomUUID(), UUID.randomUUID().toString(), null);
        UUID pagamentoId = transaction.execute(st -> {
            var o = orcamentos.save(novo(os, 1));
            o.aprovar(T0);
            return pagamentos.save(Pagamento.abrir(UUID.randomUUID(), o, "pref", "https://mp.example/c",
                    T0.plus(Duration.ofMinutes(30)), origem, T0)).getId();
        });
        transaction.executeWithoutResult(st -> {
            assertThat(pagamentos.findPendentesVencidos(T0.plus(Duration.ofMinutes(29)), PageRequest.of(0, 10))).isEmpty();
            assertThat(pagamentos.findPendentesVencidos(T0.plus(Duration.ofMinutes(30)), PageRequest.of(0, 10)))
                    .extracting(Pagamento::getId).containsExactly(pagamentoId);
            var lido = pagamentos.findParaAtualizar(pagamentoId).orElseThrow();
            assertThat(lido.getOrigem().getComandoId()).isEqualTo(origem.getComandoId());
            assertThat(pagamentos.findByOrcamentoId(lido.getOrcamentoId())).isPresent();
        });
    }
}
