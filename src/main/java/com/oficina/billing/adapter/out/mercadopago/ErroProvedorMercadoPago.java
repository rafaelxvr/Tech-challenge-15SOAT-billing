package com.oficina.billing.adapter.out.mercadopago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Extrai de um corpo de erro do Mercado Pago apenas código e mensagem permitidos (allowlist) e já sanitizados,
 * próprios para log. Nunca devolve o corpo bruto; corpo inválido ou campos fora do formato resultam em vazio.
 */
final class ErroProvedorMercadoPago {
    static final int LIMITE_MENSAGEM = 200;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CODIGO_VALIDO = Pattern.compile("[A-Za-z0-9_.\\-]{1,64}");
    private static final Pattern CONTROLE = Pattern.compile("[\\p{Cntrl}\\p{Cf}\\u2028\\u2029]+");
    private static final Pattern URL = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+\\S+");
    private static final Pattern CHAVE_MP = Pattern.compile("(?i)\\b(APP_USR|TEST)-[\\w\\-]+");
    private static final Pattern UUID_TEXTO = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
    private static final Pattern NUMERO_LONGO = Pattern.compile("\\d{6,}");
    private static final Pattern ESPACOS = Pattern.compile("\\s{2,}");

    private final String codigo;
    private final String mensagem;

    private ErroProvedorMercadoPago(String codigo, String mensagem) {
        this.codigo = codigo;
        this.mensagem = mensagem;
    }

    static ErroProvedorMercadoPago vazio() { return new ErroProvedorMercadoPago(null, null); }

    /** @param segredos valores literais (ex.: access token) que, se ecoados pelo provedor, são ocultados. */
    static ErroProvedorMercadoPago de(String corpo, List<String> segredos) {
        if (corpo == null || corpo.isBlank()) return vazio();
        JsonNode raiz;
        try {
            raiz = JSON.readTree(corpo);
        } catch (Exception e) {
            return vazio();
        }
        if (raiz == null || !raiz.isObject()) return vazio();
        JsonNode causa = raiz.path("cause").isArray() && raiz.path("cause").size() > 0 ? raiz.path("cause").get(0) : null;
        String codigo = primeiroCodigo(raiz.path("error"), causa == null ? null : causa.path("code"));
        String mensagem = primeiraMensagem(raiz.path("message"), causa == null ? null : causa.path("description"));
        return new ErroProvedorMercadoPago(codigo, mensagem == null ? null : sanitizar(mensagem, segredos));
    }

    Optional<String> codigo() { return Optional.ofNullable(codigo); }

    Optional<String> mensagem() { return Optional.ofNullable(mensagem).filter(m -> !m.isBlank()); }

    static String sanitizar(String texto, List<String> segredos) {
        String s = texto;
        for (String segredo : segredos) {
            if (segredo != null && !segredo.isBlank()) s = s.replace(segredo, "[oculto]");
        }
        s = BEARER.matcher(s).replaceAll("[oculto]");
        s = CHAVE_MP.matcher(s).replaceAll("[oculto]");
        s = URL.matcher(s).replaceAll("[url]");
        s = UUID_TEXTO.matcher(s).replaceAll("[id]");
        s = NUMERO_LONGO.matcher(s).replaceAll("[id]");
        s = CONTROLE.matcher(s).replaceAll(" ");
        s = ESPACOS.matcher(s).replaceAll(" ").strip();
        return s.length() > LIMITE_MENSAGEM ? s.substring(0, LIMITE_MENSAGEM) + "..." : s;
    }

    private static String primeiroCodigo(JsonNode... candidatos) {
        for (JsonNode c : candidatos) {
            if (c == null || !(c.isTextual() || c.isIntegralNumber())) continue;
            String valor = c.asText();
            if (CODIGO_VALIDO.matcher(valor).matches() && !NUMERO_LONGO.matcher(valor).find()) return valor;
        }
        return null;
    }

    private static String primeiraMensagem(JsonNode... candidatos) {
        for (JsonNode c : candidatos) {
            if (c != null && c.isTextual() && !c.asText().isBlank()) return c.asText();
        }
        return null;
    }
}
