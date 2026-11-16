package ar.edu.iessf.servife.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
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

import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.gestion.dto.ConteoPorMes;
import ar.edu.iessf.servife.gestion.dto.ConteoPorNombre;
import ar.edu.iessf.servife.gestion.dto.ConteoPorServicio;
import ar.edu.iessf.servife.gestion.dto.MetricasDePrestadorResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeMetricasDePrestador;

/** E16 contra PostgreSQL real: el prestador solo ve lo suyo, agrupado por estado, cliente, servicio y mes. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class ServicioDeMetricasDePrestadorTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final LocalDate DESDE = LocalDate.of(2026, 4, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 5, 31);

    @Autowired private ServicioDeMetricasDePrestador servicio;
    @Autowired private JdbcTemplate jdbc;

    private long ana;
    private long beto;
    private long yo;
    private long otro;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM solicitud_imagenes");
        jdbc.update("DELETE FROM solicitudes_servicio");
        jdbc.update("DELETE FROM prestadores");
        jdbc.update("DELETE FROM clientes");
        ana = cliente("Ana");
        beto = cliente("Beto");
        yo = prestador("Gas", "4.5");
        otro = prestador("Plomería", null);
    }

    private long cliente(String nombre) {
        return jdbc.queryForObject("INSERT INTO clientes (nombre_apellido, email, contrasenia) VALUES (?, ?, 'hash')"
            + " RETURNING id_cliente", Long.class, nombre, UUID.randomUUID() + "@mail.com");
    }

    private long prestador(String servicio, String valoracion) {
        return jdbc.queryForObject("""
            INSERT INTO prestadores (nombre_apellido, email, contrasenia, id_tipo_servicio, estado_validacion, estado_cuenta,
                valoracion_promedio)
            VALUES ('Pedro', ?, 'hash', (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = ?), 'APROBADO', 'ACTIVA',
                CAST(? AS NUMERIC))
            RETURNING id_prestador
            """, Long.class, UUID.randomUUID() + "@mail.com", servicio, valoracion);
    }

    private UUID uuid(long idPrestador) {
        return jdbc.queryForObject("SELECT uuid FROM prestadores WHERE id_prestador = ?", UUID.class, idPrestador);
    }

    private void solicitud(long cliente, long prestador, String servicio, String estado, String creadoEn) {
        jdbc.update("""
            INSERT INTO solicitudes_servicio (id_cliente, id_prestador, id_tipo_servicio, descripcion, estado, creado_en)
            VALUES (?, ?, (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = ?), 'Arreglo', ?, CAST(? AS TIMESTAMPTZ))
            """, cliente, prestador, servicio, estado, creadoEn);
    }

    @Test
    void soloCuentaLasSolicitudesDelPrestador() {
        solicitud(ana, yo, "Gas", "FINALIZADA", "2026-04-10T12:00:00-03:00");
        solicitud(ana, yo, "Gas", "EN_CURSO", "2026-05-10T12:00:00-03:00");
        solicitud(beto, yo, "Plomería", "PENDIENTE", "2026-05-11T12:00:00-03:00");   // antes de cambiar de rubro
        solicitud(beto, otro, "Plomería", "FINALIZADA", "2026-05-12T12:00:00-03:00"); // de otro prestador

        MetricasDePrestadorResponse metricas = servicio.obtener(uuid(yo), DESDE, HASTA);

        assertThat(metricas.totalSolicitudes()).isEqualTo(3);
        assertThat(metricas.porEstado()).extracting(ConteoPorNombre::nombre)
            .containsExactly("PENDIENTE", "EN_CURSO", "FINALIZADA");
        assertThat(metricas.porCliente()).containsExactly(new ConteoPorNombre("Ana", 2), new ConteoPorNombre("Beto", 1));
        assertThat(metricas.porServicio()).containsExactly(
            new ConteoPorServicio("Gas", 2, 1), new ConteoPorServicio("Plomería", 1, 0));
        assertThat(metricas.porMes()).containsExactly(new ConteoPorMes("2026-04", 1), new ConteoPorMes("2026-05", 2));
        assertThat(metricas.valoracionPromedio()).isEqualByComparingTo("4.5");
    }

    @Test
    void sinSolicitudesTodoVacioYSinValoracion() {
        MetricasDePrestadorResponse metricas = servicio.obtener(uuid(otro), DESDE, HASTA);

        assertThat(metricas.totalSolicitudes()).isZero();
        assertThat(metricas.porCliente()).isEmpty();
        assertThat(metricas.porMes()).extracting(ConteoPorMes::cantidad).containsExactly(0L, 0L);
        assertThat(metricas.valoracionPromedio()).isNull();
    }

    @Test
    void prestadorInexistenteEs404() {
        assertThatThrownBy(() -> servicio.obtener(UUID.randomUUID(), DESDE, HASTA))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
