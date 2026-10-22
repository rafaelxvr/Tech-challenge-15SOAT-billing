package com.oficina.billing.adapter.in.rest;

import com.oficina.billing.adapter.in.rest.dto.OrcamentoResponse;
import com.oficina.billing.adapter.in.rest.dto.PagamentoResponse;
import com.oficina.billing.application.ConsultaBillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Consulta de orçamentos e pagamentos (staff ADMIN)")
public class ConsultaController {
    private final ConsultaBillingService consulta;

    @GetMapping("/orcamentos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Orçamentos de uma ordem de serviço, por versão")
    public List<OrcamentoResponse> orcamentos(@RequestParam UUID osId) { return consulta.orcamentosDaOs(osId); }

    @GetMapping("/pagamentos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Situação de um pagamento")
    public PagamentoResponse pagamento(@PathVariable UUID id) { return consulta.pagamento(id); }
}
