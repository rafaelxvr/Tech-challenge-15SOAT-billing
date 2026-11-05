package com.oficina.billing.config;

import software.amazon.awssdk.profiles.Profile;
import software.amazon.awssdk.profiles.ProfileFile;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Fail-closed do perfil `aws`: o AWS SDK Java 2.x resolve endpoint por variáveis de ambiente, por system properties
 * (inclusive as específicas do SQS) e pelo arquivo AWS compartilhado (`endpoint_url` global ou `services` do perfil),
 * sem passar por nenhuma property Spring. Qualquer uma dessas fontes desviaria o tráfego do SQS real, então o startup
 * falha. O arquivo só pode ser o padrão do SDK (AWS_CONFIG_FILE/aws.configFile são recusados) e o perfil efetivo
 * (aws.profile/AWS_PROFILE ou default) é inspecionado pelo parser do próprio SDK. Só o nome da fonte vai na mensagem,
 * nunca caminho, valor ou credencial.
 */
final class EndpointsAmbientaisAws {
    static final List<String> VARIAVEIS = List.of("AWS_ENDPOINT_URL", "AWS_ENDPOINT_URL_SQS");
    static final List<String> PROPRIEDADES = List.of("aws.endpointUrl", "aws.endpointUrlSqs");
    static final String VARIAVEL_ARQUIVO = "AWS_CONFIG_FILE";
    static final String PROPRIEDADE_ARQUIVO = "aws.configFile";
    static final String PERFIL_PADRAO = "default";

    private EndpointsAmbientaisAws() {}

    static void exigirAusentes() {
        exigirAusentes(System::getenv, System::getProperty, ProfileFile::defaultProfileFile);
    }

    static void exigirAusentes(UnaryOperator<String> variaveis, UnaryOperator<String> propriedades,
                               Supplier<ProfileFile> arquivosAws) {
        VARIAVEIS.forEach(nome -> proibir("variável de ambiente", nome, variaveis.apply(nome)));
        PROPRIEDADES.forEach(nome -> proibir("system property", nome, propriedades.apply(nome)));
        proibirArquivoAlternativo("variável de ambiente", VARIAVEL_ARQUIVO, variaveis.apply(VARIAVEL_ARQUIVO));
        proibirArquivoAlternativo("system property", PROPRIEDADE_ARQUIVO, propriedades.apply(PROPRIEDADE_ARQUIVO));
        arquivosAws.get().profile(perfilEfetivo(variaveis, propriedades)).ifPresent(perfil -> {
            proibirNoPerfil(perfil, "endpoint_url");
            proibirNoPerfil(perfil, "services");
        });
    }

    private static String perfilEfetivo(UnaryOperator<String> variaveis, UnaryOperator<String> propriedades) {
        String nome = propriedades.apply("aws.profile");
        if (nome == null || nome.isBlank()) {
            nome = variaveis.apply("AWS_PROFILE");
        }
        return nome == null || nome.isBlank() ? PERFIL_PADRAO : nome.trim();
    }

    private static void proibir(String tipo, String nome, String valor) {
        if (valor != null && !valor.isBlank()) {
            throw new IllegalStateException("Perfil aws não aceita endpoint do SQS por " + tipo + " " + nome);
        }
    }

    private static void proibirArquivoAlternativo(String tipo, String nome, String valor) {
        if (valor != null && !valor.isBlank()) {
            throw new IllegalStateException("Perfil aws não aceita arquivo AWS alternativo por " + tipo + " " + nome);
        }
    }

    private static void proibirNoPerfil(Profile perfil, String propriedade) {
        if (perfil.property(propriedade).filter(valor -> !valor.isBlank()).isPresent()) {
            throw new IllegalStateException("Perfil aws não aceita " + propriedade + " no perfil efetivo do arquivo AWS");
        }
    }
}
