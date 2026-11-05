package com.oficina.billing.config;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Fail-closed do perfil `aws`: o AWS SDK Java 2.x resolve endpoint por variáveis de ambiente e por system properties
 * (inclusive as específicas do SQS), sem passar por nenhuma property Spring. Qualquer uma delas preenchida desviaria o
 * tráfego do SQS real, então o startup falha. Só o nome da fonte vai na mensagem, nunca o valor.
 */
final class EndpointsAmbientaisAws {
    static final List<String> VARIAVEIS = List.of("AWS_ENDPOINT_URL", "AWS_ENDPOINT_URL_SQS");
    static final List<String> PROPRIEDADES = List.of("aws.endpointUrl", "aws.endpointUrlSqs");

    private EndpointsAmbientaisAws() {}

    static void exigirAusentes() {
        exigirAusentes(System::getenv, System::getProperty);
    }

    static void exigirAusentes(UnaryOperator<String> variaveis, UnaryOperator<String> propriedades) {
        VARIAVEIS.forEach(nome -> proibir("variável de ambiente", nome, variaveis.apply(nome)));
        PROPRIEDADES.forEach(nome -> proibir("system property", nome, propriedades.apply(nome)));
    }

    private static void proibir(String tipo, String nome, String valor) {
        if (valor != null && !valor.isBlank()) {
            throw new IllegalStateException("Perfil aws não aceita endpoint do SQS por " + tipo + " " + nome);
        }
    }
}
