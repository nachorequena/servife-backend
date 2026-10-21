package ar.edu.iessf.servife.gestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.gestion.dto.MetricasResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeMetricas;

/** E9 contra PostgreSQL real: qué cuenta cada contador y qué queda afuera. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class ServicioDeMetricasTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ServicioDeMetricas servicio;
    @Autowired private JdbcTemplate jdbc;

    private long cliente;
    private long prestador;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM solicitud_imagenes");
        jdbc.update("DELETE FROM solicitudes_servicio");
        jdbc.update("DELETE FROM prestadores");
        jdbc.update("DELETE FROM clientes");
        cliente = jdbc.queryForObject("INSERT INTO clientes (nombre_apellido, email, contrasenia) VALUES ('Ana', 'ana@mail.com', 'hash')"
            + " RETURNING id_cliente", Long.class);
        prestador = prestador("APROBADO", false);
    }

    private long prestador(String validacion, boolean baja) {
        return jdbc.queryForObject("""
            INSERT INTO prestadores (nombre_apellido, email, contrasenia, id_tipo_servicio, estado_validacion, estado_cuenta, eliminado_en)
            VALUES ('Pedro', ?, 'hash', (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = 'Gas'), ?, 'ACTIVA',
                CASE WHEN ? THEN now() END)
            RETURNING id_prestador
            """, Long.class, UUID.randomUUID() + "@mail.com", validacion, baja);
    }

    private void solicitud(String estado, boolean baja) {
        jdbc.update("""
            INSERT INTO solicitudes_servicio (id_cliente, id_prestador, id_tipo_servicio, descripcion, estado, eliminado_en)
            VALUES (?, ?, (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = 'Gas'), 'Arreglo', ?, CASE WHEN ? THEN now() END)
            """, cliente, prestador, estado, baja);
    }

    @Test
    void sinDatosTodoEnCero() {
        jdbc.update("DELETE FROM prestadores");
        assertThat(servicio.obtener()).isEqualTo(new MetricasResponse(0, 0, 0));
    }

    @Test
    void cuentaActivasPendientesYValidacionesSinLasDadasDeBaja() {
        solicitud("ACEPTADA", false);
        solicitud("EN_CURSO", false);
        solicitud("EN_CURSO", true);
        solicitud("PENDIENTE", false);
        solicitud("PENDIENTE", false);
        for (String estado : new String[] {"RECHAZADA", "CANCELADA", "FINALIZADA", "VALORADA"}) {
            solicitud(estado, false);
        }
        prestador("PENDIENTE", false);
        prestador("PENDIENTE", true);
        prestador("RECHAZADO", false);

        assertThat(servicio.obtener()).isEqualTo(new MetricasResponse(2, 2, 1));
    }
}
