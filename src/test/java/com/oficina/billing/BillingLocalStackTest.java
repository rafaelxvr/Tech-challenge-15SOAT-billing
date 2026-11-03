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
        properties = {"billing.mensageria.enabled=true", "billing.pagamento.validade=20s"})
@ActiveProfiles({"test", "gateway-fake"})
class BillingLocalStackTest {
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
            .withServices(LocalStackContainer.Service.SQS);
    static String comandos, respostas, comandosDlq;

    static {
        LOCALSTACK.start();
        try (SqsClient sqs = sqs()) {
            comandosDlq = criarFila(sqs, "billing-commands-dlq.fifo");
            String arnDlq = sqs.getQueueAttributes(GetQueueAttributesRequest.builder().queueUrl(comandosDlq)
                    .attributeNames(QueueAttributeName.QUEUE_ARN).build()).attributes().get(QueueAttributeName.QUEUE_ARN);
            comandos = sqs.createQueue(CreateQueueRequest.builder().queueName("billing-commands.fifo")
                    .attributes(Map.of(QueueAttributeName.FIFO_QUEUE, "true", QueueAttributeName.VISIBILITY_TIMEOUT, "1",
                            QueueAttributeName.REDRIVE_POLICY,
                            "{\"deadLetterTargetArn\":\"" + arnDlq + "\",\"maxReceiveCount\":\"5\"}")).build()).queueUrl();
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

        var cabecalhos = new org.springframework.http.HttpHeaders();
        cabecalhos.setBearerAuth(com.oficina.billing.support.TokensTeste.staff("ADMIN"));
        var simulacao = http.postForEntity("/simulacoes/pagamentos/" + pagamentoId,
                new org.springframework.http.HttpEntity<>(Map.of("status", "approved"), cabecalhos), Map.class);
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

    String aprovarOrcamento(UUID os) {
        enviar(comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1)));
        aguardarResposta("OrcamentoGerado", os);
        enviar(comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "APROVADO")));
        return aguardarResposta("OrcamentoAprovado", os).payload().get("pagamentoId").asText();
    }

    @Test void decisaoRecusadaEmiteOrcamentoRecusado() {
        UUID os = UUID.randomUUID();
        enviar(comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1)));
        aguardarResposta("OrcamentoGerado", os);
        var decisao = comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "RECUSADO"));
        enviar(decisao);
        assertThat(aguardarResposta("OrcamentoRecusado", os).causationId()).isEqualTo(decisao.messageId());
    }

    @Test void pagamentoRejeitadoEmitePagamentoRecusado() {
        UUID os = UUID.randomUUID();
        String pagamentoId = aprovarOrcamento(os);
        var cabecalhos = new org.springframework.http.HttpHeaders();
        cabecalhos.setBearerAuth(com.oficina.billing.support.TokensTeste.staff("ADMIN"));
        var simulacao = http.postForEntity("/simulacoes/pagamentos/" + pagamentoId,
                new org.springframework.http.HttpEntity<>(Map.of("status", "rejected"), cabecalhos), Map.class);
        assertThat(simulacao.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(aguardarResposta("PagamentoRecusado", os).payload().get("pagamentoId").asText()).isEqualTo(pagamentoId);
    }

    @Test void pagamentoNaoPagoExpiraEEmitePagamentoExpirado() {
        UUID os = UUID.randomUUID();
        String pagamentoId = aprovarOrcamento(os);
        await().atMost(Duration.ofSeconds(90)).until(() -> {
            try {
                var expirado = aguardarRespostaCurta("PagamentoExpirado", os);
                return expirado != null && expirado.payload().get("pagamentoId").asText().equals(pagamentoId);
            } catch (RuntimeException e) {
                return false;
            }
        });
    }

    @Test void mensagemVenenosaVaiParaADlqAposCincoRecebimentos() {
        String corpo = "{\"mensagem\":\"venenosa\"}";
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(comandos).messageBody(corpo)
                .messageGroupId("grupo-venenoso").messageDeduplicationId(UUID.randomUUID().toString()).build());
        AtomicReference<Message> naDlq = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(60)).until(() -> {
            var recebidas = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(comandosDlq)
                    .maxNumberOfMessages(1).waitTimeSeconds(1).build()).messages();
            if (!recebidas.isEmpty()) naDlq.set(recebidas.get(0));
            return naDlq.get() != null;
        });
        assertThat(naDlq.get().body()).isEqualTo(corpo);
    }

    Envelope aguardarRespostaCurta(String tipo, UUID osId) {
        for (Message m : sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(respostas)
                .maxNumberOfMessages(10).waitTimeSeconds(1).build()).messages()) {
            sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(respostas).receiptHandle(m.receiptHandle()).build());
            Envelope e = mapeador.ler(m.body());
            if (e.type().equals(tipo) && e.osId().equals(osId)) return e;
        }
        return null;
    }
}
