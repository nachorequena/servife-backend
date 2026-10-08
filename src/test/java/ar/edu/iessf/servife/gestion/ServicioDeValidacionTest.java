package ar.edu.iessf.servife.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.catalogo.dto.FiltrosDeBusqueda;
import ar.edu.iessf.servife.catalogo.service.BuscadorDePrestadores;
import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.gestion.dto.Decision;
import ar.edu.iessf.servife.gestion.dto.PrestadorPendienteResponse;
import ar.edu.iessf.servife.gestion.dto.ValidacionResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeValidacion;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;

/** E5 y E6 contra PostgreSQL real. Cada test parte de prestadores y avisos vacíos. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class ServicioDeValidacionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ServicioDeValidacion servicio;
    @Autowired private BuscadorDePrestadores buscador;
    @Autowired private JdbcTemplate jdbc;

    private int contador;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM notificaciones");
        jdbc.update("DELETE FROM disponibilidad_prestador");
        jdbc.update("DELETE FROM prestadores");
    }

    /** minutosAtras mayor = más viejo. */
    private UUID prestador(String nombre, String validacion, String cuenta, boolean baja, int minutosAtras) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO prestadores (uuid, nombre_apellido, email, contrasenia, id_tipo_servicio, estado_validacion,
                estado_cuenta, creado_en, eliminado_en)
            VALUES (?, ?, ?, 'hash', (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = 'Gas'), ?, ?,
                now() - make_interval(mins => ?), CASE WHEN ? THEN now() END)
            """, uuid, nombre, "v" + (++contador) + "@mail.com", validacion, cuenta, minutosAtras, baja);
        return uuid;
    }

    private UUID pendiente(String nombre, int minutosAtras) {
        return prestador(nombre, "PENDIENTE", "ACTIVA", false, minutosAtras);
    }

    private Pagina<PrestadorPendienteResponse> pendientes() {
        return servicio.listarPendientes(PageRequest.of(0, 20));
    }

    private List<UUID> enLaBusqueda() {
        return buscador.buscar(new FiltrosDeBusqueda(null, null, null, null, null, null, null, null, null))
            .contenido().stream().map(p -> p.uuid()).toList();
    }

    private List<Map<String, Object>> avisosDe(UUID prestador) {
        return jdbc.queryForList("""
            SELECT n.titulo, n.cuerpo, n.tipo, n.rol_usuario FROM notificaciones n
            JOIN prestadores p ON p.id_prestador = n.id_usuario WHERE p.uuid = ?
            """, prestador);
    }

    @Test
    void listaSoloPendientesNoDadosDeBajaDeCualquierEstadoDeCuenta() {
        pendiente("Pendiente Activo", 10);
        prestador("Pendiente Suspendido", "PENDIENTE", "SUSPENDIDA", false, 5);
        prestador("Aprobado", "APROBADO", "ACTIVA", false, 10);
        prestador("Rechazado", "RECHAZADO", "ACTIVA", false, 10);
        prestador("Pendiente De Baja", "PENDIENTE", "ACTIVA", true, 10);

        Pagina<PrestadorPendienteResponse> r = pendientes();

        assertThat(r.contenido()).extracting(PrestadorPendienteResponse::nombreApellido)
            .containsExactly("Pendiente Activo", "Pendiente Suspendido");
        assertThat(r.totalElementos()).isEqualTo(2);
        PrestadorPendienteResponse p = r.contenido().get(0);
        assertThat(p.email()).startsWith("v").endsWith("@mail.com");
        assertThat(p.tipoServicio().nombre()).isEqualTo("Gas");
        assertThat(p.creadoEn()).isNotNull();
    }

    @Test
    void listaLosMasViejosPrimero() {
        pendiente("Nuevo", 1);
        pendiente("Viejo", 60);
        pendiente("Medio", 30);

        assertThat(pendientes().contenido()).extracting(PrestadorPendienteResponse::nombreApellido)
            .containsExactly("Viejo", "Medio", "Nuevo");
    }

    @Test
    void aprobarLoHaceAparecerEnLaBusquedaYAvisaAlPrestador() {
        UUID uuid = pendiente("Aprobable", 10);
        assertThat(enLaBusqueda()).isEmpty();

        ValidacionResponse r = servicio.decidir(uuid, Decision.APROBAR, null);

        assertThat(r).isEqualTo(new ValidacionResponse(uuid, EstadoValidacion.APROBADO));
        assertThat(enLaBusqueda()).containsExactly(uuid);
        assertThat(pendientes().contenido()).isEmpty();
        List<Map<String, Object>> avisos = avisosDe(uuid);
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0)).containsEntry("titulo", "Tu perfil fue aprobado")
            .containsEntry("cuerpo", "Tu perfil fue aprobado. Ya aparecés en las búsquedas.")
            .containsEntry("tipo", "PERFIL_APROBADO").containsEntry("rol_usuario", "PRESTADOR");
    }

    @Test
    void rechazarConMotivoAvisaConElMotivoYNoApareceEnLaBusqueda() {
        UUID uuid = pendiente("Rechazable", 10);

        ValidacionResponse r = servicio.decidir(uuid, Decision.RECHAZAR, "  Falta información  ");

        assertThat(r.estadoValidacion()).isEqualTo(EstadoValidacion.RECHAZADO);
        assertThat(enLaBusqueda()).isEmpty();
        Map<String, Object> aviso = avisosDe(uuid).get(0);
        assertThat(aviso).containsEntry("titulo", "Tu perfil fue rechazado")
            .containsEntry("cuerpo", "Tu perfil no fue aprobado. Motivo: Falta información")
            .containsEntry("tipo", "PERFIL_RECHAZADO");
    }

    @Test
    void rechazarSinMotivoOConMotivoEnBlancoNoAgregaMotivo() {
        UUID a = pendiente("Sin Motivo", 10);
        UUID b = pendiente("Motivo Blanco", 10);

        servicio.decidir(a, Decision.RECHAZAR, null);
        servicio.decidir(b, Decision.RECHAZAR, "   ");

        assertThat(avisosDe(a).get(0)).containsEntry("cuerpo", "Tu perfil no fue aprobado.");
        assertThat(avisosDe(b).get(0)).containsEntry("cuerpo", "Tu perfil no fue aprobado.");
    }

    @Test
    void decidirDosVecesDa409YNoDuplicaElAviso() {
        UUID uuid = pendiente("Doble", 10);
        servicio.decidir(uuid, Decision.APROBAR, null);

        assertThatThrownBy(() -> servicio.decidir(uuid, Decision.RECHAZAR, "tarde"))
            .isInstanceOfSatisfying(ConflictoException.class, e -> {
                assertThat(e.getCodigo()).isEqualTo("VALIDACION_YA_RESUELTA");
                assertThat(e.getMessage()).isEqualTo("Ese prestador ya fue revisado.");
            });

        assertThat(jdbc.queryForObject("SELECT estado_validacion FROM prestadores WHERE uuid = ?", String.class, uuid))
            .isEqualTo("APROBADO");
        assertThat(avisosDe(uuid)).hasSize(1);
    }

    @Test
    void prestadorInexistenteODadoDeBajaDa404() {
        UUID deBaja = prestador("Baja", "PENDIENTE", "ACTIVA", true, 10);

        assertThatThrownBy(() -> servicio.decidir(UUID.randomUUID(), Decision.APROBAR, null))
            .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.decidir(deBaja, Decision.APROBAR, null))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
