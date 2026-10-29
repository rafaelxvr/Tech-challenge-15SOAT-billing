package com.oficina.billing.adapter.out.persistence;

import com.oficina.billing.domain.Pagamento;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {
    Optional<Pagamento> findByOrcamentoId(UUID orcamentoId);

    List<Pagamento> findByEstado(com.oficina.billing.domain.EstadoPagamento estado, Pageable limite);

    /** Webhook retries and the expiry poller serialize on the payment row. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pagamento p WHERE p.id = :id")
    Optional<Pagamento> findParaAtualizar(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT p FROM Pagamento p
            WHERE p.estado = com.oficina.billing.domain.EstadoPagamento.PENDENTE AND p.expiraEm <= :agora
            ORDER BY p.expiraEm
            """)
    List<Pagamento> findPendentesVencidos(@Param("agora") Instant agora, Pageable limite);
}
