package com.oficina.billing.adapter.out.mercadopago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Extrai de um corpo de erro do Mercado Pago apenas código e mensagem que passam por uma allowlist estrita, próprios
 * para log. Nunca devolve o corpo bruto. Texto fora da allowlist, ou que se pareça com dado pessoal, credencial ou
 * identificador, é omitido por inteiro (não redigido): é preferível perder a mensagem a registrar um vazamento.
 */
final class ErroProvedorMercadoPago {
    static final int LIMITE_MENSAGEM = 200;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CODIGO_VALIDO = Pattern.compile("[A-Za-z0-9_.\\-]{1,64}");
    /** Qualquer caractere de controle/formatação/separador Unicode (C0, C1, U+2028/2029, zero-width...) vira espaço. */
    private static final Pattern CONTROLE_OU_SEPARADOR = Pattern.compile("[\\p{C}\\p{Z}]+");
    /** Única forma de mensagem aceita: letras, dígitos, espaço e pontuação inócua. Sem aspas, barra invertida, @, : ou =. */
    private static final Pattern MENSAGEM_PERMITIDA = Pattern.compile("[\\p{L}\\p{N} .,()\\-_/]+");
    private static final Pattern PALAVRA_SENSIVEL = Pattern.compile(
            "(?i)bearer|password|passwd|senha|secret|token|authorization|api[ _.\\-]?key|app_usr|cpf|cnpj|phone|telefone|e-?mail");
    private static final Pattern UUID_TEXTO = Pattern.compile(
            "(?i)[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}");
    private static final Pattern HEX_LONGO = Pattern.compile("(?i)[0-9a-f]{16,}");
    private static final Pattern SEPARADOR_ENTRE_DIGITOS = Pattern.compile("(?<=\\p{Nd})[ .,()\\-_/]+(?=\\p{Nd})");
    private static final Pattern NUMERO_LONGO = Pattern.compile("\\p{Nd}{6,}");
    private static final Pattern ESPACOS = Pattern.compile(" {2,}");

    private final String codigo;
    private final String mensagem;

    private ErroProvedorMercadoPago(String codigo, String mensagem) {
        this.codigo = codigo;
        this.mensagem = mensagem;
    }

    static ErroProvedorMercadoPago vazio() { return new ErroProvedorMercadoPago(null, null); }

    /** @param segredos valores literais (ex.: access token) que, se ecoados pelo provedor, fazem o texto ser omitido. */
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
        String codigo = primeiroCodigo(segredos, raiz.path("error"), causa == null ? null : causa.path("code"));
        String mensagem = primeiraMensagem(segredos, raiz.path("message"), causa == null ? null : causa.path("description"));
        return new ErroProvedorMercadoPago(codigo, mensagem);
    }

    Optional<String> codigo() { return Optional.ofNullable(codigo); }

    Optional<String> mensagem() { return Optional.ofNullable(mensagem).filter(m -> !m.isBlank()); }

    /** @return a mensagem normalizada e truncada, ou vazio se não passar pela allowlist. */
    static Optional<String> sanitizar(String texto, List<String> segredos) {
        if (texto == null) return Optional.empty();
        if (contemSegredo(texto, segredos)) return Optional.empty();
        String s = normalizar(texto);
        if (s.isEmpty() || !MENSAGEM_PERMITIDA.matcher(s).matches() || pareceSensivel(s, segredos)) return Optional.empty();
        return Optional.of(s.length() > LIMITE_MENSAGEM ? s.substring(0, LIMITE_MENSAGEM) + "..." : s);
    }

    private static String normalizar(String texto) {
        return ESPACOS.matcher(CONTROLE_OU_SEPARADOR.matcher(texto).replaceAll(" ")).replaceAll(" ").strip();
    }

    /**
     * Compara o segredo literal com o texto bruto e também com o texto e o segredo normalizados pela mesma função,
     * para que tab, separador Unicode ou repetição de espaços não escondam um segredo que a normalização revelaria.
     */
    private static boolean contemSegredo(String texto, List<String> segredos) {
        String normalizado = normalizar(texto);
        for (String segredo : segredos) {
            if (segredo == null || segredo.isBlank()) continue;
            if (texto.contains(segredo)) return true;
            String segredoNormalizado = normalizar(segredo);
            if (!segredoNormalizado.isEmpty() && normalizado.contains(segredoNormalizado)) return true;
        }
        return false;
    }

    private static boolean pareceSensivel(String s, List<String> segredos) {
        if (contemSegredo(s, segredos)) return true;
        return PALAVRA_SENSIVEL.matcher(s).find()
                || UUID_TEXTO.matcher(s).find()
                || HEX_LONGO.matcher(s).find()
                || NUMERO_LONGO.matcher(SEPARADOR_ENTRE_DIGITOS.matcher(s).replaceAll("")).find();
    }

    private static String primeiroCodigo(List<String> segredos, JsonNode... candidatos) {
        for (JsonNode c : candidatos) {
            if (c == null || !(c.isTextual() || c.isIntegralNumber())) continue;
            String valor = c.asText();
            if (CODIGO_VALIDO.matcher(valor).matches() && !pareceSensivel(valor, segredos)) return valor;
        }
        return null;
    }

    private static String primeiraMensagem(List<String> segredos, JsonNode... candidatos) {
        for (JsonNode c : candidatos) {
            if (c == null || !c.isTextual()) continue;
            Optional<String> limpa = sanitizar(c.asText(), segredos);
            if (limpa.isPresent()) return limpa.get();
        }
        return null;
    }
}
