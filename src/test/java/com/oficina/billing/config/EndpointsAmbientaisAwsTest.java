package com.oficina.billing.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.profiles.ProfileFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fail-closed do perfil aws contra redirecionamento do SQS pelo arquivo AWS compartilhado. */
class EndpointsAmbientaisAwsTest {
    static ProfileFile arquivo(String conteudo) {
        return ProfileFile.builder().type(ProfileFile.Type.CONFIGURATION)
                .content(new ByteArrayInputStream(conteudo.getBytes(StandardCharsets.UTF_8))).build();
    }

    /** Os testes que passam pelo ambiente real só são conclusivos sem AWS_CONFIG_FILE/aws.configFile na máquina. */
    static boolean ambienteRealSemArquivoAwsAlternativo() {
        return System.getenv("AWS_CONFIG_FILE") == null && System.getProperty("aws.configFile") == null;
    }

    private static UnaryOperator<String> de(Map<String, String> valores) {
        return valores::get;
    }

    private static void exigir(Map<String, String> env, Map<String, String> props, String conteudo) {
        EndpointsAmbientaisAws.exigirAusentes(de(env), de(props), () -> arquivo(conteudo));
    }

    @Test void aceitaArquivoSemEndpointNemServices() {
        assertThatCode(() -> exigir(Map.of(), Map.of(), "[default]\nregion = us-east-1\n")).doesNotThrowAnyException();
        assertThatCode(() -> exigir(Map.of(), Map.of(), "")).doesNotThrowAnyException();
    }

    @Test void recusaAwsConfigFileConfigurado() {
        assertThatThrownBy(() -> exigir(Map.of("AWS_CONFIG_FILE", "C:/caminho-sigiloso/config"), Map.of(), ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Perfil aws não aceita arquivo AWS alternativo por variável de ambiente AWS_CONFIG_FILE");
    }

    @Test void recusaAwsConfigFileSystemProperty() {
        assertThatThrownBy(() -> exigir(Map.of(), Map.of("aws.configFile", "C:/caminho-sigiloso/config"), ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Perfil aws não aceita arquivo AWS alternativo por system property aws.configFile");
    }

    @Test void ignoraAwsConfigFileEmBranco() {
        assertThatCode(() -> exigir(Map.of("AWS_CONFIG_FILE", " "), Map.of("aws.configFile", ""), ""))
                .doesNotThrowAnyException();
    }

    @Test void recusaEndpointUrlGlobalDoPerfilDefault() {
        assertThatThrownBy(() -> exigir(Map.of(), Map.of(), "[default]\nendpoint_url = http://usuario:segredo@host\n"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Perfil aws não aceita endpoint_url no perfil efetivo do arquivo AWS");
    }

    @Test void recusaServicesNoPerfilDefault() {
        String conteudo = "[default]\nservices = meus\n\n[services meus]\nsqs =\n  endpoint_url = http://segredo\n";
        assertThatThrownBy(() -> exigir(Map.of(), Map.of(), conteudo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Perfil aws não aceita services no perfil efetivo do arquivo AWS");
    }

    @Test void recusaEndpointNoPerfilNomeadoPorAwsProfile() {
        String conteudo = "[profile lab]\nendpoint_url = http://segredo\n";
        assertThatThrownBy(() -> exigir(Map.of("AWS_PROFILE", "lab"), Map.of(), conteudo))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("endpoint_url");
        assertThatThrownBy(() -> exigir(Map.of(), Map.of("aws.profile", "lab"), conteudo))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("endpoint_url");
    }

    @Test void ignoraEndpointDeOutroPerfilNaoSelecionado() {
        String conteudo = "[profile outro]\nendpoint_url = http://x\n[default]\nregion = us-east-1\n";
        assertThatCode(() -> exigir(Map.of(), Map.of(), conteudo)).doesNotThrowAnyException();
    }

    @Test void mensagemNaoVazaCaminhoEndpointOuCredencial() {
        assertThatThrownBy(() -> exigir(Map.of(), Map.of(), "[default]\nendpoint_url = http://segredo\n"))
                .hasMessageNotContaining("segredo").hasMessageNotContaining("http");
        assertThatThrownBy(() -> exigir(Map.of("AWS_CONFIG_FILE", "C:/segredo/config"), Map.of(), ""))
                .hasMessageNotContaining("segredo").hasMessageNotContaining("C:");
    }
}
