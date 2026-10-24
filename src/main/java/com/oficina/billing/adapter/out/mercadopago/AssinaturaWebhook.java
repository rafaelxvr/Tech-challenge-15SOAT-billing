package com.oficina.billing.adapter.out.mercadopago;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/** Mercado Pago x-signature: HMAC-SHA256 hex of "id:{data.id};request-id:{x-request-id};ts:{ts};" plus freshness. */
public class AssinaturaWebhook {
    private final byte[] segredo;
    private final Duration tolerancia;
    private final Clock clock;

    public AssinaturaWebhook(String segredo, Duration tolerancia, Clock clock) {
        this.segredo = segredo == null ? new byte[0] : segredo.getBytes(StandardCharsets.UTF_8);
        this.tolerancia = tolerancia;
        this.clock = clock;
    }

    public boolean valida(String xSignature, String xRequestId, String dataId) {
        if (segredo.length == 0 || vazio(xSignature) || vazio(xRequestId) || vazio(dataId)) return false;
        Map<String, String> partes = partes(xSignature);
        String ts = partes.get("ts");
        String v1 = partes.get("v1");
        if (vazio(ts) || vazio(v1) || !recente(ts)) return false;
        String id = dataId.matches("[A-Za-z0-9]+") ? dataId.toLowerCase(Locale.ROOT) : dataId;
        String manifesto = "id:" + id + ";request-id:" + xRequestId + ";ts:" + ts + ";";
        byte[] esperado = hmac(manifesto).getBytes(StandardCharsets.US_ASCII);
        byte[] recebido = v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(esperado, recebido);
    }

    private boolean recente(String ts) {
        long valor;
        try { valor = Long.parseLong(ts); } catch (NumberFormatException e) { return false; }
        Instant instante = valor > 100_000_000_000L ? Instant.ofEpochMilli(valor) : Instant.ofEpochSecond(valor);
        return Duration.between(instante, clock.instant()).abs().compareTo(tolerancia) <= 0;
    }

    private String hmac(String manifesto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível");
        }
    }

    private static Map<String, String> partes(String cabecalho) {
        Map<String, String> partes = new HashMap<>();
        for (String parte : cabecalho.split(",")) {
            String[] kv = parte.split("=", 2);
            if (kv.length == 2) partes.put(kv[0].trim(), kv[1].trim());
        }
        return partes;
    }

    private static boolean vazio(String valor) { return valor == null || valor.isBlank(); }
}
