package com.oficina.billing.mensagem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Consumer side: every canonical command fixture is valid and decodes into its payload type. */
class ContratoComandosTest {
    final JsonSchemaFactory schemas = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    final ObjectMapper json = new ObjectMapper();
    final MapeadorMensagens mapeador = new MapeadorMensagens();

    @ParameterizedTest
    @EnumSource(TipoComando.class)
    void fixtureRespeitaContratoEDecodifica(TipoComando tipo) throws Exception {
        JsonNode payload = json.readTree(Files.readString(Path.of("contracts", "fixtures", tipo.tipo() + ".json"))).get("payload");
        var schema = schemas.getSchema(Files.readString(Path.of("contracts", tipo.tipo(), "v1.json")));
        assertThat(schema.validate(payload)).isEmpty();
        Class<?> alvo = switch (tipo) {
            case GERAR_ORCAMENTO -> GerarOrcamentoPayload.class;
            case REGISTRAR_DECISAO -> RegistrarDecisaoPayload.class;
            case CANCELAR_ORCAMENTO, ESTORNAR_PAGAMENTO -> CompensacaoPayload.class;
        };
        assertThat(mapeador.converter(payload, alvo)).isNotNull();
    }
}
