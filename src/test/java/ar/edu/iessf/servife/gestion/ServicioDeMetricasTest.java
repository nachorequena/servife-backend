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

import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.gestion.dto.ConteoPorMes;
import ar.edu.iessf.servife.gestion.dto.ConteoPorNombre;
import ar.edu.iessf.servife.gestion.dto.ConteoPorServicio;
import ar.edu.iessf.servife.gestion.dto.MetricasDetalleResponse;
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

    private void solicitudDel(long idPrestador, String servicio, String estado, String creadoEn, boolean baja) {
        jdbc.update("""
            INSERT INTO solicitudes_servicio (id_cliente, id_prestador, id_tipo_servicio, descripcion, estado, creado_en, eliminado_en)
            VALUES (?, ?, (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = ?), 'Arreglo', ?,
                CAST(? AS TIMESTAMPTZ), CASE WHEN ? THEN now() END)
            """, cliente, idPrestador, servicio, estado, creadoEn, baja);
    }

    @Test
    void detalleAgrupaPorServicioEstadoMesYPrestadorDentroDelPeriodo() {
        long otro = prestador("APROBADO", false);
        solicitudDel(prestador, "Gas", "FINALIZADA", "2026-03-10T12:00:00-03:00", false);
        solicitudDel(prestador, "Gas", "VALORADA", "2026-03-20T12:00:00-03:00", false);
        solicitudDel(prestador, "Gas", "PENDIENTE", "2026-05-01T12:00:00-03:00", false);
        solicitudDel(otro, "Plomería", "FINALIZADA", "2026-05-02T12:00:00-03:00", false);
        solicitudDel(otro, "Plomería", "FINALIZADA", "2026-05-03T12:00:00-03:00", true);    // baja: no cuenta
        solicitudDel(otro, "Plomería", "FINALIZADA", "2026-07-01T00:30:00-03:00", false);   // fuera del período
        solicitudDel(otro, "Plomería", "ACEPTADA", "2026-06-30T23:30:00-03:00", false);     // 02:30 UTC del 1/7: cuenta junio

        MetricasDetalleResponse detalle = servicio.obtenerDetalle(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 30));

        assertThat(detalle.totalSolicitudes()).isEqualTo(5);
        assertThat(detalle.porServicio()).containsExactly(
            new ConteoPorServicio("Gas", 3, 2), new ConteoPorServicio("Plomería", 2, 1));
        assertThat(detalle.porEstado()).extracting(ConteoPorNombre::nombre)
            .containsExactly("PENDIENTE", "ACEPTADA", "FINALIZADA", "VALORADA");
        assertThat(detalle.porMes()).containsExactly(new ConteoPorMes("2026-03", 2), new ConteoPorMes("2026-04", 0),
            new ConteoPorMes("2026-05", 2), new ConteoPorMes("2026-06", 1));
        assertThat(detalle.topPrestadores()).extracting(ConteoPorNombre::cantidad).containsExactly(2L, 1L);
    }

    @Test
    void detalleSinFechasSonLosUltimosSeisMesesYValidaElPeriodo() {
        MetricasDetalleResponse detalle = servicio.obtenerDetalle(null, null);

        assertThat(detalle.porMes()).hasSize(6).allMatch(m -> m.cantidad() == 0);
        assertThat(detalle.desde().getDayOfMonth()).isEqualTo(1);
        assertThatThrownBy(() -> servicio.obtenerDetalle(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1)))
            .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> servicio.obtenerDetalle(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 4, 1)))
            .isInstanceOf(ValidacionException.class);
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
