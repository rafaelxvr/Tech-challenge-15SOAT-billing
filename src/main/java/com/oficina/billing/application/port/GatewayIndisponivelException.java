package com.oficina.billing.application.port;

/** Provider unavailable or answered unexpectedly; carries a symbolic code, never provider text. */
public class GatewayIndisponivelException extends RuntimeException {
    private final String codigo;

    public GatewayIndisponivelException(String codigo) {
        super(codigo);
        this.codigo = codigo;
    }

    public String codigo() { return codigo; }
}
