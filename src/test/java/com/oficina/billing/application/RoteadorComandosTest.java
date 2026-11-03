package com.oficina.billing.application;

import com.oficina.billing.mensagem.Envelope;
import com.oficina.billing.mensagem.TipoComando;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoteadorComandosTest {
    record Fixo(TipoComando tipo, List<TipoComando> chamados) implements ManipuladorComando {
        public void executar(Envelope comando) { chamados.add(tipo); }
    }

    @Test void encaminhaPorTipoERecusaDuplicados() {
        List<TipoComando> chamados = new ArrayList<>();
        var roteador = new RoteadorComandos(List.of(new Fixo(TipoComando.GERAR_ORCAMENTO, chamados)));
        assertThat(roteador.suporta(TipoComando.GERAR_ORCAMENTO)).isTrue();
        assertThat(roteador.suporta(TipoComando.ESTORNAR_PAGAMENTO)).isFalse();
        roteador.aplicar(TipoComando.GERAR_ORCAMENTO, null);
        assertThat(chamados).containsExactly(TipoComando.GERAR_ORCAMENTO);
        assertThatThrownBy(() -> new RoteadorComandos(List.of(new Fixo(TipoComando.GERAR_ORCAMENTO, chamados),
                new Fixo(TipoComando.GERAR_ORCAMENTO, chamados)))).isInstanceOf(IllegalStateException.class);
    }
}
