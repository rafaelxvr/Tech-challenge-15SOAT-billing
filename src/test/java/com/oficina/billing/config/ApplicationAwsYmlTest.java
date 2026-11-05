package com.oficina.billing.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** O perfil aws não versiona endpoint, chave estática nem as credenciais dummy do LocalStack. */
class ApplicationAwsYmlTest {
    @Test void perfilAwsNaoDefineEndpointNemCredenciais() throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-aws.yml"));
        Properties props = yaml.getObject();
        assertThat(props.getProperty("billing.mensageria.endpoint", "")).isBlank();
        assertThat(props.stringPropertyNames()).noneMatch(k -> k.toLowerCase().matches(".*(access-?key|secret|credential|token).*"));
        String texto = new String(new ClassPathResource("application-aws.yml").getInputStream().readAllBytes());
        assertThat(texto).doesNotContain("localstack", "127.0.0.1", "localhost", "test/test", "AKIA");
    }
}
