package com.oficina.billing.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plan 2 Local Kind, Task 2: the migration role owns the schema, the runtime role only has the DML the service uses
 * and cannot reach another database. Uses the very script the Kind PostgreSQL pod runs.
 */
class PapeisBancoTest {
    static final String SENHA_MIGRACAO = "senha-migracao-" + UUID.randomUUID();
    static final String SENHA_APP = "senha-app-" + UUID.randomUUID();
    static PostgreSQLContainer<?> postgres;

    @BeforeAll static void iniciar() throws Exception {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("billing")
                .withEnv("BILLING_MIGRATOR_PASSWORD", SENHA_MIGRACAO).withEnv("BILLING_APP_PASSWORD", SENHA_APP)
                .withCopyFileToContainer(MountableFile.forHostPath("k8s/local-deps/postgres-init/01-roles.sh", 0755),
                        "/docker-entrypoint-initdb.d/01-roles.sh");
        postgres.start();
        try (Connection admin = conectar(postgres.getUsername(), postgres.getPassword(), "billing");
             Statement s = admin.createStatement()) {
            s.execute("CREATE DATABASE oficina");
            s.execute("REVOKE CONNECT ON DATABASE oficina FROM PUBLIC");
        }
        Flyway.configure().dataSource(url("billing"), "billing_migrator", SENHA_MIGRACAO).load().migrate();
    }

    @AfterAll static void encerrar() { postgres.stop(); }

    static String url(String banco) {
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + banco;
    }

    static Connection conectar(String usuario, String senha, String banco) throws SQLException {
        return DriverManager.getConnection(url(banco), usuario, senha);
    }

    @Test void migracaoRodaComoPapelDeMigracaoEOEsquemaPertenceAEle() throws Exception {
        try (Connection c = conectar("billing_migrator", SENHA_MIGRACAO, "billing"); Statement s = c.createStatement()) {
            var r = s.executeQuery("SELECT tableowner FROM pg_tables WHERE tablename='orcamento'");
            assertThat(r.next()).isTrue();
            assertThat(r.getString(1)).isEqualTo("billing_migrator");
        }
    }

    @Test void papelDeExecucaoFazDmlDoServicoMasNaoDdl() throws Exception {
        try (Connection c = conectar("billing_app", SENHA_APP, "billing"); Statement s = c.createStatement()) {
            UUID mensagem = UUID.randomUUID();
            s.execute("INSERT INTO inbox(message_id, tipo) VALUES ('" + mensagem + "', 'GerarOrcamento')");
            s.execute("UPDATE inbox SET tipo='RegistrarDecisao' WHERE message_id='" + mensagem + "'");
            assertThat(s.executeQuery("SELECT count(*) FROM inbox").next()).isTrue();
            s.execute("INSERT INTO webhook_evento(request_id, data_id) VALUES ('r', 'd')");
            s.execute("DELETE FROM webhook_evento");
            assertThatThrownBy(() -> s.execute("DELETE FROM inbox")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.execute("CREATE TABLE intruso(id int)")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.execute("DROP TABLE inbox")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.execute("TRUNCATE outbox")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.execute("ALTER TABLE inbox ADD COLUMN x int")).isInstanceOf(SQLException.class);
        }
    }

    @Test void papelDeExecucaoUsaAIdentidadeDoOutbox() throws Exception {
        try (Connection c = conectar("billing_app", SENHA_APP, "billing"); Statement s = c.createStatement()) {
            UUID id = UUID.randomUUID();
            s.execute("INSERT INTO outbox(message_id, os_id, tipo, envelope) VALUES ('" + id + "', '" + UUID.randomUUID()
                    + "', 'OrcamentoGerado', '{}'::jsonb)");
            var r = s.executeQuery("SELECT sequencia FROM outbox WHERE message_id='" + id + "'");
            assertThat(r.next()).isTrue();
        }
    }

    @Test void papelDeExecucaoNaoAcessaOutroBanco() {
        assertThatThrownBy(() -> conectar("billing_app", SENHA_APP, "oficina").close()).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> conectar("billing_migrator", SENHA_MIGRACAO, "oficina").close()).isInstanceOf(SQLException.class);
    }

    @Test void papeisSemPrivilegiosAdministrativos() throws Exception {
        try (Connection c = conectar("billing_app", SENHA_APP, "billing"); Statement s = c.createStatement()) {
            var r = s.executeQuery("SELECT rolsuper OR rolcreatedb OR rolcreaterole FROM pg_roles WHERE rolname='billing_app'");
            assertThat(r.next()).isTrue();
            assertThat(r.getBoolean(1)).isFalse();
        }
    }
}
