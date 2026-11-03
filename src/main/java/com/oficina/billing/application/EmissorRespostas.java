package com.oficina.billing.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oficina.billing.application.port.SaidaMensagensPort;
import com.oficina.billing.domain.ReferenciaComando;
import com.oficina.billing.mensagem.FabricaRespostas;
import com.oficina.billing.mensagem.TipoResposta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmissorRespostas {
    private final FabricaRespostas fabrica;
    private final SaidaMensagensPort saida;

    public void emitir(ReferenciaComando origem, TipoResposta tipo, ObjectNode payload) {
        saida.registrar(fabrica.responder(origem, tipo.tipo(), payload));
    }
}
