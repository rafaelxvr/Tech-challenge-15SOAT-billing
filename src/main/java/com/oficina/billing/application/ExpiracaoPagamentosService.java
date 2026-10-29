package com.oficina.billing.application;

import com.oficina.billing.adapter.out.persistence.PagamentoRepository;
import com.oficina.billing.domain.Pagamento;
import com.oficina.billing.mensagem.PayloadsResposta;
import com.oficina.billing.mensagem.TipoResposta;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpiracaoPagamentosService {
    private final PagamentoRepository pagamentos;
    private final EmissorRespostas emissor;
    private final Clock clock;

    @Transactional
    public int expirarVencidos(int limite) {
        Instant agora = clock.instant();
        List<Pagamento> vencidos = pagamentos.findPendentesVencidos(agora, PageRequest.of(0, limite));
        for (Pagamento pagamento : vencidos) {
            if (pagamento.expirar(agora)) {
                emissor.emitir(pagamento.getOrigem(), TipoResposta.PAGAMENTO_EXPIRADO, PayloadsResposta.pagamento(pagamento));
            }
        }
        return vencidos.size();
    }
}
