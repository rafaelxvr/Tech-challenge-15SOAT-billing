package com.oficina.billing.application;

import com.oficina.billing.adapter.in.rest.dto.OrcamentoResponse;
import com.oficina.billing.adapter.in.rest.dto.PagamentoResponse;
import com.oficina.billing.adapter.out.persistence.OrcamentoRepository;
import com.oficina.billing.adapter.out.persistence.PagamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsultaBillingService {
    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;

    @Transactional(readOnly = true)
    public List<OrcamentoResponse> orcamentosDaOs(UUID osId) {
        return orcamentos.findByOsIdOrderByVersaoAsc(osId).stream().map(o -> new OrcamentoResponse(o.getId(), o.getOsId(),
                o.getVersao(), o.getEstado().name(), o.getTotal(), o.getCriadoEm(), o.getItens().stream()
                .map(i -> new OrcamentoResponse.Item(i.getTipo().name(), i.getReferenciaId(), i.getDescricao(),
                        i.getQuantidade(), i.getPrecoUnitario())).toList())).toList();
    }

    @Transactional(readOnly = true)
    public PagamentoResponse pagamento(UUID id) {
        var p = pagamentos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento", id));
        return new PagamentoResponse(p.getId(), p.getOrcamentoId(), p.getOsId(), p.getEstado().name(), p.getValor(),
                p.getCheckoutUrl(), p.getExpiraEm());
    }
}
