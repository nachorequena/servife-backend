package ar.edu.iessf.servife.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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

import ar.edu.iessf.servife.catalogo.dto.PrestadorDetalleResponse;
import ar.edu.iessf.servife.catalogo.service.ServicioDeFichaDePrestador;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;

/** B6 contra PostgreSQL real: ficha, visibilidad y contador de servicios realizados. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class ServicioDeFichaDePrestadorTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ServicioDeFichaDePrestador ficha;
    @Autowired private JdbcTemplate jdbc;

    private int contador;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM solicitudes_servicio");
        jdbc.update("DELETE FROM disponibilidad_prestador");
        jdbc.update("DELETE FROM prestadores");
        jdbc.update("DELETE FROM clientes");
    }

    private UUID prestador(String validacion, String cuenta, boolean baja) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO prestadores (uuid, nombre_apellido, email, contrasenia, id_tipo_servicio, estado_validacion,
                estado_cuenta, zona, descripcion, eliminado_en)
            VALUES (?, 'Marcos Gasista', ?, 'hash', (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = 'Gas'),
                ?, ?, 'Centro', 'Instalaciones de gas', CASE WHEN ? THEN now() END)
            """, uuid, "p" + (++contador) + "@mail.com", validacion, cuenta, baja);
        return uuid;
    }

    private UUID aprobado() {
        return prestador("APROBADO", "ACTIVA", false);
    }

    private void dia(UUID prestador, int dia, boolean baja) {
        jdbc.update("INSERT INTO disponibilidad_prestador (id_prestador, dia_semana, eliminado_en) "
            + "SELECT id_prestador, ?, CASE WHEN ? THEN now() END FROM prestadores WHERE uuid = ?", dia, baja, prestador);
    }

    private void solicitud(UUID prestador, String estado, boolean baja) {
        jdbc.update("""
            INSERT INTO solicitudes_servicio (id_cliente, id_prestador, id_tipo_servicio, descripcion, estado, eliminado_en)
            VALUES ((SELECT min(id_cliente) FROM clientes), (SELECT id_prestador FROM prestadores WHERE uuid = ?),
                (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = 'Gas'), 'Arreglo', ?,
                CASE WHEN ? THEN now() END)
            """, prestador, estado, baja);
    }

    private void cliente() {
        jdbc.update("INSERT INTO clientes (nombre_apellido, email, contrasenia) VALUES ('Ana', 'ana@mail.com', 'hash')");
    }

    @Test
    void devuelveLaFichaCompleta() {
        UUID id = aprobado();
        dia(id, 5, false);
        dia(id, 2, false);
        dia(id, 7, true);
        jdbc.update("UPDATE prestadores SET valoracion_promedio = 4.5 WHERE uuid = ?", id);

        PrestadorDetalleResponse r = ficha.obtener(id);

        assertThat(r.uuid()).isEqualTo(id);
        assertThat(r.nombreApellido()).isEqualTo("Marcos Gasista");
        assertThat(r.tipoServicio().nombre()).isEqualTo("Gas");
        assertThat(r.zona()).isEqualTo("Centro");
        assertThat(r.descripcion()).isEqualTo("Instalaciones de gas");
        assertThat(r.dias()).containsExactly(2, 5);
        assertThat(r.valoracionPromedio()).isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(r.serviciosRealizados()).isZero();
        assertThat(r.verificado()).isTrue();
    }

    @Test
    void prestadorCuyoTipoFueDadoDeBajaEs404() {
        UUID id = aprobado();
        jdbc.update("UPDATE tipos_servicio SET eliminado_en = now() WHERE nombre = 'Gas'");
        try {
            assertThatThrownBy(() -> ficha.obtener(id)).isInstanceOf(RecursoNoEncontradoException.class);
        } finally {
            jdbc.update("UPDATE tipos_servicio SET eliminado_en = NULL WHERE nombre = 'Gas'");
        }
    }

    @Test
    void sinValoracionNiDiasLaFichaVieneVacia() {
        PrestadorDetalleResponse r = ficha.obtener(aprobado());
        assertThat(r.valoracionPromedio()).isNull();
        assertThat(r.dias()).isEmpty();
    }

    @Test
    void cuentaSoloFinalizadasYValoradasNoDadasDeBaja() {
        cliente();
        UUID id = aprobado();
        UUID otro = aprobado();
        solicitud(id, "FINALIZADA", false);
        solicitud(id, "VALORADA", false);
        solicitud(id, "VALORADA", true);
        solicitud(id, "PENDIENTE", false);
        solicitud(id, "ACEPTADA", false);
        solicitud(id, "EN_CURSO", false);
        solicitud(id, "CANCELADA", false);
        solicitud(id, "RECHAZADA", false);
        solicitud(otro, "FINALIZADA", false);

        assertThat(ficha.obtener(id).serviciosRealizados()).isEqualTo(2);
        assertThat(ficha.obtener(otro).serviciosRealizados()).isEqualTo(1);
    }

    @Test
    void noVisibleEsNoEncontrado() {
        for (UUID id : new UUID[] {prestador("PENDIENTE", "ACTIVA", false), prestador("RECHAZADO", "ACTIVA", false),
                prestador("APROBADO", "SUSPENDIDA", false), prestador("APROBADO", "ACTIVA", true),
                UUID.randomUUID()}) {
            assertThatThrownBy(() -> ficha.obtener(id)).isInstanceOf(RecursoNoEncontradoException.class);
        }
    }
}
