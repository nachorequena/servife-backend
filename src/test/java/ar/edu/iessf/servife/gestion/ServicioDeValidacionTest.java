package ar.edu.iessf.servife.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

import ar.edu.iessf.servife.catalogo.dto.ActualizarPerfilDePrestadorRequest;
import ar.edu.iessf.servife.catalogo.dto.FiltrosDeBusqueda;
import ar.edu.iessf.servife.catalogo.service.BuscadorDePrestadores;
import ar.edu.iessf.servife.catalogo.service.ServicioDePerfilDePrestador;
import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.gestion.dto.Decision;
import ar.edu.iessf.servife.gestion.dto.PrestadorPendienteResponse;
import ar.edu.iessf.servife.gestion.dto.ValidacionResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeValidacion;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;

/** E5 y E6 contra PostgreSQL real. Cada test parte de prestadores y avisos vacíos y de dos gestores de prueba. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
class ServicioDeValidacionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ServicioDeValidacion servicio;
    @Autowired private BuscadorDePrestadores buscador;
    @Autowired private ServicioDePerfilDePrestador perfil;
    @Autowired private JdbcTemplate jdbc;

    private int contador;
    private UUID gestor;
    private UUID otroGestor;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM notificaciones");
        jdbc.update("DELETE FROM disponibilidad_prestador");
        jdbc.update("DELETE FROM prestadores");
        jdbc.update("DELETE FROM gestores WHERE email LIKE 'gestor-e6-%'");
        gestor = gestor("gestor-e6-a@mail.com");
        otroGestor = gestor("gestor-e6-b@mail.com");
    }

    private UUID gestor(String email) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("INSERT INTO gestores (uuid, nombre_apellido, email, contrasenia) VALUES (?, 'Gestor E6', ?, 'hash')",
            uuid, email);
        return uuid;
    }

    /** minutosAtras mayor = más viejo; se postuló al darse de alta. */
    private UUID prestador(String nombre, String validacion, String cuenta, boolean baja, int minutosAtras) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO prestadores (uuid, nombre_apellido, email, contrasenia, id_tipo_servicio, estado_validacion,
                estado_cuenta, creado_en, postulado_en, eliminado_en)
            VALUES (?, ?, ?, 'hash', (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = 'Gas'), ?, ?,
                now() - make_interval(mins => ?), now() - make_interval(mins => ?), CASE WHEN ? THEN now() END)
            """, uuid, nombre, "v" + (++contador) + "@mail.com", validacion, cuenta, minutosAtras, minutosAtras, baja);
        return uuid;
    }

    private ValidacionResponse decidir(UUID prestador, Decision decision, String motivo) {
        return servicio.decidir(prestador, decision, motivo, gestor);
    }

    private Map<String, Object> auditoria(UUID prestador) {
        return jdbc.queryForMap("""
            SELECT g.uuid AS gestor, p.validado_en, p.motivo_validacion FROM prestadores p
            LEFT JOIN gestores g ON g.id_gestor = p.validado_por WHERE p.uuid = ?
            """, prestador);
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
        assertThat(p.postuladoEn()).isNotNull();
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

        ValidacionResponse r = decidir(uuid, Decision.APROBAR, null);

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

        ValidacionResponse r = decidir(uuid, Decision.RECHAZAR, "  Falta información  ");

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

        decidir(a, Decision.RECHAZAR, null);
        decidir(b, Decision.RECHAZAR, "   ");

        assertThat(avisosDe(a).get(0)).containsEntry("cuerpo", "Tu perfil no fue aprobado.");
        assertThat(avisosDe(b).get(0)).containsEntry("cuerpo", "Tu perfil no fue aprobado.");
    }

    @Test
    void decidirDosVecesDa409YNoDuplicaElAviso() {
        UUID uuid = pendiente("Doble", 10);
        decidir(uuid, Decision.APROBAR, null);

        assertThatThrownBy(() -> decidir(uuid, Decision.RECHAZAR, "tarde"))
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

        assertThatThrownBy(() -> decidir(UUID.randomUUID(), Decision.APROBAR, null))
            .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> decidir(deBaja, Decision.APROBAR, null))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }
    @Test
    void ordenaPorLaFechaDePostulacionNoPorLaDeAlta() {
        UUID viejoRepostulado = pendiente("Alta vieja, cambió de rubro", 120);
        jdbc.update("UPDATE prestadores SET postulado_en = now() - make_interval(mins => 1) WHERE uuid = ?", viejoRepostulado);
        pendiente("Esperando hace una hora", 60);

        assertThat(pendientes().contenido()).extracting(PrestadorPendienteResponse::nombreApellido)
            .containsExactly("Esperando hace una hora", "Alta vieja, cambió de rubro");
    }

    @Test
    void decidirGuardaQuienCuandoYElMotivoAunqueSeaUnaAprobacion() {
        UUID rechazado = pendiente("Rechazado", 10);
        UUID aprobado = pendiente("Aprobado con nota", 10);

        decidir(rechazado, Decision.RECHAZAR, "  Fotos borrosas ");
        decidir(aprobado, Decision.APROBAR, "Revisé la matrícula por teléfono");

        assertThat(auditoria(rechazado)).containsEntry("gestor", gestor)
            .containsEntry("motivo_validacion", "Fotos borrosas");
        assertThat(auditoria(rechazado).get("validado_en")).isNotNull();
        assertThat(auditoria(aprobado)).containsEntry("motivo_validacion", "Revisé la matrícula por teléfono");
        // Al prestador aprobado no le llega la nota interna.
        assertThat(avisosDe(aprobado).get(0)).containsEntry("cuerpo", "Tu perfil fue aprobado. Ya aparecés en las búsquedas.");
    }

    @Test
    void cambiarDeRubroLoVuelveAlFinalDeLaColaYBorraLaDecisionAnterior() {
        UUID uuid = pendiente("Rechazado que se repostula", 60);
        decidir(uuid, Decision.RECHAZAR, "Rubro equivocado");
        UUID plomeria = jdbc.queryForObject("SELECT uuid FROM tipos_servicio WHERE nombre = 'Plomería'", UUID.class);

        perfil.actualizarPerfil(uuid, new ActualizarPerfilDePrestadorRequest(plomeria, null, null, null, null, null));

        Map<String, Object> despues = auditoria(uuid);
        assertThat(despues.get("gestor")).isNull();
        assertThat(despues.get("validado_en")).isNull();
        assertThat(despues.get("motivo_validacion")).isNull();
        assertThat(pendientes().contenido()).extracting(PrestadorPendienteResponse::uuid).containsExactly(uuid);
        assertThat(jdbc.queryForObject("SELECT postulado_en > creado_en + interval '30 minutes' FROM prestadores WHERE uuid = ?",
            Boolean.class, uuid)).isTrue();
    }

    @Test
    void dosGestoresDecidiendoALaVezSoloUnoGanaYHayUnSoloAviso() throws Exception {
        UUID uuid = pendiente("Disputado", 10);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Future<ValidacionResponse>> resultados = new ArrayList<>();
        try {
            resultados.add(hilos.submit(() -> {
                largada.await();
                return servicio.decidir(uuid, Decision.APROBAR, null, gestor);
            }));
            resultados.add(hilos.submit(() -> {
                largada.await();
                return servicio.decidir(uuid, Decision.RECHAZAR, null, otroGestor);
            }));
            largada.countDown();
            int ganadores = 0;
            int conflictos = 0;
            for (Future<ValidacionResponse> r : resultados) {
                try {
                    r.get(30, TimeUnit.SECONDS);
                    ganadores++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOfSatisfying(ConflictoException.class,
                        c -> assertThat(c.getCodigo()).isEqualTo("VALIDACION_YA_RESUELTA"));
                    conflictos++;
                }
            }
            assertThat(ganadores).isEqualTo(1);
            assertThat(conflictos).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(avisosDe(uuid)).hasSize(1);
        assertThat(auditoria(uuid).get("gestor")).isIn(gestor, otroGestor);
    }
}
