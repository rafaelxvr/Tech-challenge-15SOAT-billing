package com.oficina.billing.mensagem;

import java.util.Arrays;
import java.util.Optional;

public enum TipoComando {
    GERAR_ORCAMENTO("GerarOrcamento"),
    REGISTRAR_DECISAO("RegistrarDecisao"),
    CANCELAR_ORCAMENTO("CancelarOrcamento"),
    ESTORNAR_PAGAMENTO("EstornarPagamento");

    private final String tipo;
    TipoComando(String tipo) { this.tipo = tipo; }
    public String tipo() { return tipo; }

    public static Optional<TipoComando> deTipo(String tipo) {
        return Arrays.stream(values()).filter(c -> c.tipo.equals(tipo)).findFirst();
    }
}
