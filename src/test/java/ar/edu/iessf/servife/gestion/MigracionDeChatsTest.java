package ar.edu.iessf.servife.gestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V7 crea el chat de las solicitudes que ya existían antes de la migración. Sin Spring: Flyway directo. */
@Testcontainers(disabledWithoutDocker = true)
class MigracionDeChatsTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private Flyway flyway(String hasta) {
        var config = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration");
        return (hasta == null ? config : config.target(hasta)).load();
    }

    @Test
    void lasSolicitudesAnterioresAV7TienenSuChat() {
        flyway("6").migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        Long cliente = jdbc.queryForObject(
            "INSERT INTO clientes (nombre_apellido, email, contrasenia) VALUES ('Ana', 'ana@mail.com', 'x') RETURNING id_cliente",
            Long.class);
        Long tipo = jdbc.queryForObject("SELECT min(id_tipo_servicio) FROM tipos_servicio", Long.class);
        Long prestador = jdbc.queryForObject(
            "INSERT INTO prestadores (nombre_apellido, email, contrasenia, id_tipo_servicio) VALUES ('Beto', 'beto@mail.com', 'x', ?) RETURNING id_prestador",
            Long.class, tipo);
        for (int i = 0; i < 2; i++) {
            jdbc.update("INSERT INTO solicitudes_servicio (id_cliente, id_prestador, id_tipo_servicio, descripcion) VALUES (?, ?, ?, 'Pérdida')",
                cliente, prestador, tipo);
        }

        flyway(null).migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM chats", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM solicitudes_servicio s WHERE NOT EXISTS (SELECT 1 FROM chats c WHERE c.id_solicitud = s.id_solicitud)",
            Long.class)).isZero();
    }
}
