package com.oficina.billing.adapter.out.persistence;

import com.oficina.billing.domain.Orcamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrcamentoRepository extends JpaRepository<Orcamento, UUID> {
    Optional<Orcamento> findByOsIdAndVersao(UUID osId, int versao);

    List<Orcamento> findByOsIdOrderByVersaoAsc(UUID osId);
}
