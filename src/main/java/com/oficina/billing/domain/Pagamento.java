package com.oficina.billing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One Mercado Pago charge for an approved quote. */
@Entity
@Table(name = "pagamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pagamento {
    @Id private UUID id;
    @Column(name = "orcamento_id", nullable = false, updatable = false) private UUID orcamentoId;
    @Column(name = "os_id", nullable = false, updatable = false) private UUID osId;
    @Column(nullable = false, precision = 12, scale = 2, updatable = false) private BigDecimal valor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EstadoPagamento estado;
    @Column(name = "mp_preference_id", length = 100) private String mpPreferenceId;
    @Column(name = "mp_payment_id", length = 100) private String mpPaymentId;
    @Column(name = "checkout_url", nullable = false, length = 2048) private String checkoutUrl;
    @Column(name = "expira_em", nullable = false) private Instant expiraEm;
    @Embedded private ReferenciaComando origem;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm;
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm;
    @Version @Column(name = "versao_registro", nullable = false) private Long versaoRegistro;

    public static Pagamento abrir(UUID id, Orcamento orcamento, String mpPreferenceId, String checkoutUrl,
                                  Instant expiraEm, ReferenciaComando origem, Instant agora) {
        if (orcamento == null || orcamento.getEstado() != EstadoOrcamento.APROVADO) {
            throw new RegraNegocioException("Pagamento exige orçamento aprovado");
        }
        if (checkoutUrl == null || !checkoutUrl.startsWith("https://")) {
            throw new RegraNegocioException("URL de checkout deve ser HTTPS");
        }
        if (expiraEm == null || !expiraEm.isAfter(agora)) throw new RegraNegocioException("Validade deve ser futura");
        Pagamento p = new Pagamento();
        p.id = Objects.requireNonNull(id, "id");
        p.orcamentoId = orcamento.getId();
        p.osId = orcamento.getOsId();
        p.valor = orcamento.getTotal();
        p.estado = EstadoPagamento.PENDENTE;
        p.mpPreferenceId = mpPreferenceId;
        p.checkoutUrl = checkoutUrl;
        p.expiraEm = expiraEm;
        p.origem = Objects.requireNonNull(origem, "origem");
        p.criadoEm = agora;
        p.atualizadoEm = agora;
        return p;
    }

    public void confirmar(String mpPaymentId, Instant agora) {
        exigir(EstadoPagamento.PENDENTE);
        this.mpPaymentId = Objects.requireNonNull(mpPaymentId, "mpPaymentId");
        mudar(EstadoPagamento.CONFIRMADO, agora);
    }

    public void recusar(String mpPaymentId, Instant agora) {
        exigir(EstadoPagamento.PENDENTE);
        this.mpPaymentId = Objects.requireNonNull(mpPaymentId, "mpPaymentId");
        mudar(EstadoPagamento.RECUSADO, agora);
    }

    public boolean expirar(Instant agora) {
        if (estado != EstadoPagamento.PENDENTE || agora.isBefore(expiraEm)) return false;
        mudar(EstadoPagamento.EXPIRADO, agora);
        return true;
    }

    public boolean cancelar(Instant agora) {
        if (estado != EstadoPagamento.PENDENTE) return false;
        mudar(EstadoPagamento.CANCELADO, agora);
        return true;
    }

    public boolean estornar(Instant agora) {
        if (estado == EstadoPagamento.ESTORNADO) return false;
        exigir(EstadoPagamento.CONFIRMADO);
        mudar(EstadoPagamento.ESTORNADO, agora);
        return true;
    }

    /** Money arrived after the saga gave up on this charge: it is refunded, never confirmed. */
    public void estornarConfirmacaoTardia(String mpPaymentId, Instant agora) {
        if (!encerradoSemCobranca()) throw new RegraNegocioException("Confirmação tardia exige pagamento encerrado");
        this.mpPaymentId = Objects.requireNonNull(mpPaymentId, "mpPaymentId");
        mudar(EstadoPagamento.ESTORNADO, agora);
    }

    public boolean encerradoSemCobranca() {
        return estado == EstadoPagamento.CANCELADO || estado == EstadoPagamento.EXPIRADO;
    }

    private void exigir(EstadoPagamento esperado) {
        if (estado != esperado) {
            throw new RegraNegocioException("Pagamento %s em %s; esperado %s".formatted(id, estado, esperado));
        }
    }

    private void mudar(EstadoPagamento novo, Instant agora) {
        estado = novo;
        atualizadoEm = Objects.requireNonNull(agora);
    }
}
