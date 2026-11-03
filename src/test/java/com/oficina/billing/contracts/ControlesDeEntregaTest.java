package com.oficina.billing.contracts;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controles S-3, S-5, S-7 e S-10 sobre os artefatos de entrega: manifests, Dockerfile e workflow. Falham no `verify`
 * se alguém reintroduzir imagem sem digest, escape para AWS, pod privilegiado ou ação sem SHA fixado.
 */
class ControlesDeEntregaTest {
    static final Path RAIZ = Path.of(".");

    static String ler(String caminho) throws IOException { return Files.readString(RAIZ.resolve(caminho)); }

    static List<Path> arquivos(String diretorio) throws IOException {
        try (Stream<Path> s = Files.walk(RAIZ.resolve(diretorio))) {
            return s.filter(Files::isRegularFile).toList();
        }
    }

    @Test void imagensBaseEDeDependenciasFicamFixadasPorDigest() throws IOException {
        Pattern from = Pattern.compile("^FROM (\\S+)", Pattern.MULTILINE);
        Matcher m = from.matcher(ler("Dockerfile"));
        int total = 0;
        while (m.find()) {
            total++;
            assertThat(m.group(1)).as("FROM %s", m.group(1)).matches(".+@sha256:[0-9a-f]{64}");
        }
        assertThat(total).isGreaterThanOrEqualTo(2);
        for (Path manifesto : arquivos("k8s/local-deps")) {
            if (!manifesto.toString().endsWith(".yaml")) continue;
            Matcher imagens = Pattern.compile("^\\s*image: (\\S+)", Pattern.MULTILINE).matcher(Files.readString(manifesto));
            while (imagens.find()) {
                assertThat(imagens.group(1)).as("%s", manifesto).matches(".+@sha256:[0-9a-f]{64}");
            }
        }
    }

    @Test void imagemRodaComoUsuarioNaoRoot() throws IOException {
        assertThat(ler("Dockerfile")).containsPattern("(?m)^USER 10001:10001$");
    }

    @Test void deploymentDoServicoEndurecido() throws IOException {
        String deployment = ler("k8s/base/deployment.yaml");
        assertThat(deployment).contains("runAsNonRoot: true", "readOnlyRootFilesystem: true",
                "allowPrivilegeEscalation: false", "automountServiceAccountToken: false", "drop: [\"ALL\"]",
                "seccompProfile: {type: RuntimeDefault}");
        assertThat(deployment).containsPattern("limits: \\{cpu: \"1\", memory: 1Gi\\}");
        assertThat(deployment).containsPattern("requests: \\{cpu: 250m, memory: 512Mi\\}");
        assertThat(ler("k8s/base/serviceaccount.yaml")).contains("automountServiceAccountToken: false");
    }

    @Test void dependenciasLocaisTambemTemLimitesESemTokenDeServiceAccount() throws IOException {
        for (String arquivo : List.of("postgres-billing", "localstack", "queue-init")) {
            String yaml = ler("k8s/local-deps/" + arquivo + ".yaml");
            assertThat(yaml).as(arquivo).contains("automountServiceAccountToken: false").contains("limits:");
        }
    }

    @Test void fakeSoExisteNoOverlayDeTesteENuncaNoBaseNemNaImagem() throws IOException {
        for (Path p : arquivos("k8s/base")) assertThat(Files.readString(p)).as("%s", p).doesNotContain("gateway-fake");
        assertThat(ler("Dockerfile")).doesNotContain("gateway-fake");
        assertThat(ler("k8s/overlays/kind-fake/perfil-fake.yaml")).contains("kind,gateway-fake");
        for (Path p : arquivos("k8s/overlays/kind-sandbox")) assertThat(Files.readString(p)).doesNotContain("gateway-fake");
        assertThat(ler("src/main/resources/application.yml")).doesNotContain("gateway-fake");
        assertThat(ler("src/main/resources/application-kind.yml")).doesNotContain("gateway-fake");
    }

    @Test void semEscapeParaAwsNosManifestsNemNoWorkflow() throws IOException {
        List<Path> alvos = new java.util.ArrayList<>(arquivos("k8s"));
        alvos.add(RAIZ.resolve(".github/workflows/ci.yml"));
        alvos.add(RAIZ.resolve("scripts/deploy-kind.sh"));
        for (Path p : alvos) {
            String conteudo = Files.readString(p);
            assertThat(conteudo).as("%s", p).doesNotContain("amazonaws.com").doesNotContain("AWS_PROFILE")
                    .doesNotContain("AWS_ROLE_ARN").doesNotContain("AWS_WEB_IDENTITY_TOKEN_FILE")
                    .doesNotContain("configure-aws-credentials").doesNotContain("id-token")
                    .doesNotContain("eks.amazonaws.com");
        }
    }

    @Test void workflowSegueOControleS7() throws IOException {
        String ci = ler(".github/workflows/ci.yml");
        assertThat(ci).containsPattern("(?m)^permissions:\\n  contents: read$");
        assertThat(ci).doesNotContain("pull_request_target");
        Matcher usos = Pattern.compile("(?m)^\\s*uses: (\\S+)").matcher(ci);
        int total = 0;
        while (usos.find()) {
            total++;
            assertThat(usos.group(1)).as("ação %s", usos.group(1)).matches(".+@[0-9a-f]{40}");
        }
        assertThat(total).isGreaterThan(0);
        assertThat(ci).contains("distributionSha256Sum", "gitleaks", "trivy", "scan-artifacts.sh", "kind-deploy");
        assertThat(ci).containsPattern("TRIVY_IMAGE: aquasec/trivy@sha256:[0-9a-f]{64}");
        assertThat(ci).containsPattern("GITLEAKS_IMAGE: zricethezav/gitleaks@sha256:[0-9a-f]{64}");
        assertThat(ci).doesNotContain("secrets.SONAR_TOKEN }}\n        run: echo");
    }

    @Test void mavenWrapperTemChecksumDeDistribuicao() throws IOException {
        assertThat(ler(".mvn/wrapper/maven-wrapper.properties")).containsPattern("(?m)^distributionSha256Sum=[0-9a-f]{64}$");
    }
}
