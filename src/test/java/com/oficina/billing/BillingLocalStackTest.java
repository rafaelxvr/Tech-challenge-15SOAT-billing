package com.oficina.billing;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oficina.billing.mensagem.Envelope;
import com.oficina.billing.mensagem.MapeadorMensagens;
import com.oficina.billing.support.Comandos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "billing.mensageria.enabled=true")
@ActiveProfiles({"test", "gateway-fake"})
class BillingLocalStackTest {
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
            .withServices(LocalStackContainer.Service.SQS);
    static String comandos, respostas;

    static {
        LOCALSTACK.start();
        try (SqsClient sqs = sqs()) {
            comandos = criarFila(sqs, "billing-commands.fifo");
            respostas = criarFila(sqs, "os-saga-replies.fifo");
        }
    }

    static SqsClient sqs() {
        return SqsClient.builder().endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SQS))
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))).build();
    }

    static String criarFila(SqsClient sqs, String nome) {
        return sqs.createQueue(CreateQueueRequest.builder().queueName(nome)
                .attributes(Map.of(QueueAttributeName.FIFO_QUEUE, "true")).build()).queueUrl();
    }

    @DynamicPropertySource static void propriedades(DynamicPropertyRegistry p) {
        p.add("billing.mensageria.region", LOCALSTACK::getRegion);
        p.add("billing.mensageria.endpoint", () -> LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
        p.add("billing.mensageria.fila-comandos", () -> comandos);
        p.add("billing.mensageria.fila-respostas", () -> respostas);
    }

    @Autowired MapeadorMensagens mapeador;
    @Autowired TestRestTemplate http;
    final SqsClient sqs = sqs();

    void enviar(Envelope comando) {
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(comandos).messageBody(mapeador.escrever(comando))
                .messageGroupId(comando.osId().toString()).messageDeduplicationId(comando.messageId().toString()).build());
    }

    Envelope aguardarResposta(String tipo, UUID osId) {
        AtomicReference<Envelope> achada = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            for (Message m : sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(respostas)
                    .maxNumberOfMessages(10).waitTimeSeconds(1).build()).messages()) {
                sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(respostas).receiptHandle(m.receiptHandle()).build());
                Envelope e = mapeador.ler(m.body());
                if (e.type().equals(tipo) && e.osId().equals(osId)) achada.set(e);
            }
            return achada.get() != null;
        });
        return achada.get();
    }

    Envelope comando(String tipo, UUID os, int passo, ObjectNode payload) { return Comandos.comando(tipo, os, passo, payload); }

    @Test void orcamentoAprovacaoPagamentoEEstorno() {
        UUID os = UUID.randomUUID();
        var gerar = comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1));
        enviar(gerar);
        var gerado = aguardarResposta("OrcamentoGerado", os);
        assertThat(gerado.causationId()).isEqualTo(gerar.messageId());
        assertThat(gerado.payload().get("total").decimalValue()).isEqualByComparingTo("151.00");

        var decisao = comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "APROVADO"));
        enviar(decisao);
        var aprovado = aguardarResposta("OrcamentoAprovado", os);
        String pagamentoId = aprovado.payload().get("pagamentoId").asText();
        assertThat(aprovado.payload().get("checkoutUrl").asText()).startsWith("https://pagamento.fake.local/checkout/");

        var simulacao = http.postForEntity("/simulacoes/pagamentos/" + pagamentoId, Map.of("status", "approved"), Map.class);
        assertThat(simulacao.getStatusCode().is2xxSuccessful()).isTrue();
        var confirmado = aguardarResposta("PagamentoConfirmado", os);
        assertThat(confirmado.causationId()).isEqualTo(decisao.messageId());

        var estorno = comando("EstornarPagamento", os, 5, Comandos.compensacao(1, "ESTOQUE_INSUFICIENTE"));
        enviar(estorno);
        assertThat(aguardarResposta("PagamentoEstornado", os).payload().get("pagamentoId").asText()).isEqualTo(pagamentoId);

        enviar(comando("CancelarOrcamento", os, 5, Comandos.compensacao(1, "ESTOQUE_INSUFICIENTE")));
        assertThat(aguardarResposta("OrcamentoCancelado", os).payload().get("orcamentoId").asText())
                .isEqualTo(gerado.payload().get("orcamentoId").asText());
    }
}
