package ar.edu.iessf.servife;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Levanta la app completa contra un PostgreSQL 16 real: Flyway aplica las migraciones y
 * Hibernate valida el esquema. Necesita Docker; sin Docker se saltea.
 */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class MigracionesTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void lasMigracionesCreanLasTablasDeV1() {
        List<String> tablas = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tablas).contains(
            "clientes", "prestadores", "gestores", "tipos_servicio", "disponibilidad_prestador",
            "solicitudes_servicio", "solicitud_imagenes", "valoraciones", "publicaciones",
            "publicacion_imagenes", "archivos", "documentos_validacion", "dispositivos", "notificaciones",
            "refresh_tokens", "codigos_recuperacion");
    }

    @Test
    void cargaLosSieteRubrosIniciales() {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM tipos_servicio WHERE eliminado_en IS NULL", Integer.class);
        Boolean gas = jdbc.queryForObject("SELECT requiere_matricula FROM tipos_servicio WHERE nombre = 'Gas'", Boolean.class);
        assertThat(total).isEqualTo(7);
        assertThat(gas).isTrue();
    }
}
