package com.oficina.billing.application;

import com.oficina.billing.adapter.out.persistence.PagamentoRepository;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.domain.EstadoPagamento;
import com.oficina.billing.domain.Pagamento;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Re-query fallback for environments where the Mercado Pago webhook cannot reach the service (no approved tunnel):
 * looks the payment up by our external reference and applies it through the same verified path as a webhook, which
 * re-reads the payment and checks reference, amount and currency again.
 */
@Service
@RequiredArgsConstructor
public class ReconsultaPagamentoService {
    private final PagamentoRepository pagamentos;
    private final GatewayPagamento gateway;
    private final ConfirmacaoPagamentoService confirmacao;

    /** Returns true when the provider had a payment for this charge and it was applied. */
    public boolean reconsultar(UUID pagamentoId) {
        Pagamento pagamento = pagamentos.findById(pagamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento", pagamentoId));
        if (!reconsultavel(pagamento)) return false;
        return gateway.buscarPorReferencia(pagamentoId).map(situacao -> {
            confirmacao.processarNotificacao(situacao.mpPaymentId());
            return true;
        }).orElse(false);
    }

    public int reconsultarPendentes(int limite) {
        int aplicados = 0;
        for (Pagamento pagamento : pagamentos.findByEstado(EstadoPagamento.PENDENTE, PageRequest.of(0, limite))) {
            if (reconsultar(pagamento.getId())) aplicados++;
        }
        return aplicados;
    }

    private static boolean reconsultavel(Pagamento pagamento) {
        return pagamento.getEstado() == EstadoPagamento.PENDENTE || pagamento.encerradoSemCobranca();
    }
}
