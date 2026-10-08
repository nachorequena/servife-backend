package ar.edu.iessf.servife.solicitudes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import ar.edu.iessf.servife.solicitudes.domain.SolicitudDePrueba;
import ar.edu.iessf.servife.solicitudes.repository.SolicitudRepository;

/** C4 (PATCH /solicitudes/{uuid}/estado) contra PostgreSQL real, con avisos a la contraparte. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CambioDeEstadoTest {

    private static final String API = "/api/v1";
    /** 9/10/2026 a las 00:30 en Argentina: ya es el día del trabajo, pero en UTC todavía son las 03:30 del 9. */
    private static final Instant ESA_NOCHE = Instant.parse("2026-10-09T03:30:00Z");

    /** Reloj fijo; el día del trabajo de los escenarios es el 9/10/2026. */
    @TestConfiguration
    static class RelojFijo {
        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(ESA_NOCHE, ZoneId.of("UTC"));
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private ClienteRepository clientes;
    @Autowired private PrestadorRepository prestadores;
    @Autowired private TipoServicioRepository tiposServicio;
    @Autowired private SolicitudRepository solicitudes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    private Cliente ana;
    private Prestador beto;
    private TipoServicio tipo;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM notificaciones");
        tipo = tiposServicio.findByEliminadoEnIsNullOrderByNombre().get(0);
        ana = new Cliente("Ana Pérez", UUID.randomUUID() + "@mail.com", "hash");
        ana.setTelefono("1111");
        ana = clientes.save(ana);
        beto = new Prestador("Beto Gas", UUID.randomUUID() + "@mail.com", "hash", tipo);
        beto.setTelefono("2222");
        beto.setEstadoValidacion(EstadoValidacion.APROBADO);
        beto = prestadores.save(beto);
    }

    private Solicitud solicitud(EstadoSolicitud estado, LocalDate fecha) {
        Solicitud s = new Solicitud(ana, beto, tipo, "Pérdida en la cocina", fecha, "mañana", "Calle 1 123");
        SolicitudDePrueba.enEstado(s, estado);
        return solicitudes.saveAndFlush(s);
    }

    private Solicitud solicitud(EstadoSolicitud estado) {
        return solicitud(estado, LocalDate.of(2026, 10, 9));
    }

    private static JwtRequestPostProcessor como(UUID uuid, String rol) {
        return jwt().jwt(j -> j.subject(uuid.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private JwtRequestPostProcessor comoAna() {
        return como(ana.getUuid(), "CLIENTE");
    }

    private JwtRequestPostProcessor comoBeto() {
        return como(beto.getUuid(), "PRESTADOR");
    }

    private ResultActions patchear(Solicitud s, JwtRequestPostProcessor quien, String cuerpo) throws Exception {
        return mvc.perform(patch(API + "/solicitudes/" + s.getUuid() + "/estado").contextPath(API)
            .contentType("application/json").content(cuerpo).with(quien));
    }

    private List<Map<String, Object>> avisosDe(String rolUsuario, Long idUsuario) {
        return jdbc.queryForList("SELECT tipo, titulo, cuerpo, uuid_solicitud FROM notificaciones WHERE rol_usuario = ? "
            + "AND id_usuario = ? ORDER BY id_notificacion", rolUsuario, idUsuario);
    }

    @Test
    void aceptarGuardaElPrecioMuestraElTelefonoYAvisaAlCliente() throws Exception {
        Solicitud s = solicitud(EstadoSolicitud.PENDIENTE);

        patchear(s, comoBeto(), "{\"accion\":\"ACEPTAR\",\"precioAcordado\":800050}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("ACEPTADA"))
            .andExpect(jsonPath("$.precioAcordado").value(800050))
            .andExpect(jsonPath("$.cliente.telefono").value("1111"))
            .andExpect(jsonPath("$.prestador.telefono").value("2222"))
            .andExpect(jsonPath("$.accionesDisponibles", hasItems("INICIAR", "CANCELAR")));

        List<Map<String, Object>> avisos = avisosDe("CLIENTE", ana.getId());
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0)).containsEntry("tipo", "SOLICITUD_ACEPTADA")
            .containsEntry("titulo", "Aceptaron tu solicitud")
            .containsEntry("cuerpo", "Beto Gas aceptó la solicitud del 09/10/2026. Precio acordado: $ 8.000,50")
            .containsEntry("uuid_solicitud", s.getUuid());
        assertThat(avisosDe("PRESTADOR", beto.getId())).isEmpty();
    }

    @Test
    void aceptarSinPrecioFunciona() throws Exception {
        Solicitud s = solicitud(EstadoSolicitud.PENDIENTE);
        patchear(s, comoBeto(), "{\"accion\":\"ACEPTAR\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.precioAcordado").value(nullValue()));
        assertThat(avisosDe("CLIENTE", ana.getId()).get(0))
            .containsEntry("cuerpo", "Beto Gas aceptó la solicitud del 09/10/2026.");
    }

    @Test
    void rechazarGuardaMotivoYAvisaAlCliente() throws Exception {
        Solicitud s = solicitud(EstadoSolicitud.PENDIENTE);

        patchear(s, comoBeto(), "{\"accion\":\"RECHAZAR\",\"motivo\":\"  No llego a esa fecha  \"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("RECHAZADA"))
            .andExpect(jsonPath("$.motivo").value("No llego a esa fecha"))
            .andExpect(jsonPath("$.canceladaPor").value(nullValue()));

        assertThat(avisosDe("CLIENTE", ana.getId())).singleElement().satisfies(a ->
            assertThat(a).containsEntry("tipo", "SOLICITUD_RECHAZADA").containsEntry("titulo", "Rechazaron tu solicitud")
                .containsEntry("cuerpo", "Beto Gas rechazó la solicitud del 09/10/2026. Motivo: No llego a esa fecha"));
    }

    @Test
    void motivoEnBlancoEsSinMotivo() throws Exception {
        Solicitud s = solicitud(EstadoSolicitud.PENDIENTE);
        patchear(s, comoBeto(), "{\"accion\":\"RECHAZAR\",\"motivo\":\"   \"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.motivo").value(nullValue()));
        assertThat(avisosDe("CLIENTE", ana.getId()).get(0))
            .containsEntry("cuerpo", "Beto Gas rechazó la solicitud del 09/10/2026.");
    }

    @Test
    void elClienteCancelaYGuardaMotivoYQuienCancelo() throws Exception {
        Solicitud s = solicitud(EstadoSolicitud.ACEPTADA);

        patchear(s, comoAna(), "{\"accion\":\"CANCELAR\",\"motivo\":\"Ya lo arreglé\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CANCELADA"))
            .andExpect(jsonPath("$.motivo").value("Ya lo arreglé"))
            .andExpect(jsonPath("$.canceladaPor").value("CLIENTE"));

        assertThat(avisosDe("PRESTADOR", beto.getId())).singleElement().satisfies(a ->
            assertThat(a).containsEntry("tipo", "SOLICITUD_CANCELADA").containsEntry("titulo", "Cancelaron la solicitud")
                .containsEntry("cuerpo", "Ana Pérez canceló la solicitud del 09/10/2026. Motivo: Ya lo arreglé"));
        assertThat(avisosDe("CLIENTE", ana.getId())).isEmpty();
    }

    @Test
    void elClienteCancelaUnaPendienteConMotivoYAvisaSoloAlPrestador() throws Exception {
        Solicitud s = solicitud(EstadoSolicitud.PENDIENTE);

        patchear(s, comoAna(), "{\"accion\":\"CANCELAR\",\"motivo\":\"Cambié de planes\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CANCELADA"))
            .andExpect(jsonPath("$.canceladaPor").value("CLIENTE"))
            .andExpect(jsonPath("$.motivo").value("Cambié de planes"));

        assertThat(avisosDe("PRESTADOR", beto.getId())).singleElement().satisfies(a ->
            assertThat(a).containsEntry("tipo", "SOLICITUD_CANCELADA").containsEntry("titulo", "Cancelaron la solicitud")
                .containsEntry("cuerpo", "Ana Pérez canceló la solicitud del 09/10/2026. Motivo: Cambié de planes")
                .containsEntry("uuid_solicitud", s.getUuid()));
        assertThat(avisosDe("CLIENTE", ana.getId())).isEmpty();
    }

    @Test
    void elPrestadorCancelaUnaAceptadaYAvisaAlCliente() throws Exception {
        Solicitud s = solicitud(EstadoSolicitud.ACEPTADA);
        patchear(s, comoBeto(), "{\"accion\":\"CANCELAR\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canceladaPor").value("PRESTADOR"));
        assertThat(avisosDe("CLIENTE", ana.getId())).singleElement()
            .satisfies(a -> assertThat(a).containsEntry("tipo", "SOLICITUD_CANCELADA")
                .containsEntry("titulo", "Cancelaron la solicitud")
                .containsEntry("cuerpo", "Beto Gas canceló la solicitud del 09/10/2026."));
    }

    @Test
    void iniciarYFinalizarAvisanAlCliente() throws Exception {
        Solicitud s = solicitud(EstadoSolicitud.ACEPTADA);

        patchear(s, comoBeto(), "{\"accion\":\"INICIAR\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("EN_CURSO"))
            .andExpect(jsonPath("$.accionesDisponibles", hasItem("FINALIZAR")));
        patchear(s, comoBeto(), "{\"accion\":\"FINALIZAR\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("FINALIZADA"));

        List<Map<String, Object>> avisos = avisosDe("CLIENTE", ana.getId());
        assertThat(avisos).hasSize(2);
        assertThat(avisos.get(0)).containsEntry("tipo", "SOLICITUD_EN_CURSO").containsEntry("titulo", "Empezó el trabajo")
            .containsEntry("cuerpo", "Beto Gas inició la solicitud del 09/10/2026.");
        assertThat(avisos.get(1)).containsEntry("tipo", "SOLICITUD_FINALIZADA").containsEntry("titulo", "Terminó el trabajo")
            .containsEntry("cuerpo", "Beto Gas finalizó la solicitud del 09/10/2026.");
    }

    @Test
    void iniciarAntesDeLaFechaEs409YElDiaDeLaFechaYaEs200() throws Exception {
        Solicitud futura = solicitud(EstadoSolicitud.ACEPTADA, LocalDate.of(2026, 10, 10));
        patchear(futura, comoBeto(), "{\"accion\":\"INICIAR\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("TODAVIA_NO_ES_LA_FECHA"))
            .andExpect(jsonPath("$.mensaje").value("Todavía no llegó la fecha del trabajo."));
        assertThat(estadoDe(futura)).isEqualTo("ACEPTADA");
        assertThat(avisosDe("CLIENTE", ana.getId())).isEmpty();

        // 00:30 en Argentina del 9/10 (03:30 UTC): hoy ya es el 9
        Solicitud deHoy = solicitud(EstadoSolicitud.ACEPTADA, LocalDate.of(2026, 10, 9));
        patchear(deHoy, comoBeto(), "{\"accion\":\"INICIAR\"}").andExpect(status().isOk());
    }

    @Test
    void transicionesInvalidasSon409() throws Exception {
        patchear(solicitud(EstadoSolicitud.PENDIENTE), comoBeto(), "{\"accion\":\"INICIAR\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        patchear(solicitud(EstadoSolicitud.ACEPTADA), comoBeto(), "{\"accion\":\"ACEPTAR\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        patchear(solicitud(EstadoSolicitud.EN_CURSO), comoAna(), "{\"accion\":\"CANCELAR\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        patchear(solicitud(EstadoSolicitud.FINALIZADA), comoBeto(), "{\"accion\":\"FINALIZAR\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        patchear(solicitud(EstadoSolicitud.CANCELADA), comoBeto(), "{\"accion\":\"ACEPTAR\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        patchear(solicitud(EstadoSolicitud.RECHAZADA), comoAna(), "{\"accion\":\"CANCELAR\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones", Integer.class)).isZero();
    }

    @Test
    void actorEquivocadoEs403() throws Exception {
        Solicitud pendiente = solicitud(EstadoSolicitud.PENDIENTE);
        patchear(pendiente, comoAna(), "{\"accion\":\"ACEPTAR\"}").andExpect(status().isForbidden());
        patchear(pendiente, comoBeto(), "{\"accion\":\"CANCELAR\"}").andExpect(status().isForbidden());
        patchear(solicitud(EstadoSolicitud.ACEPTADA), comoAna(), "{\"accion\":\"INICIAR\"}").andExpect(status().isForbidden());
        patchear(solicitud(EstadoSolicitud.EN_CURSO), comoAna(), "{\"accion\":\"FINALIZAR\"}").andExpect(status().isForbidden());
        assertThat(estadoDe(pendiente)).isEqualTo("PENDIENTE");
    }

    @Test
    void quienNoEsParteRecibe404YUnGestorRecibe403() throws Exception {
        Solicitud s = solicitud(EstadoSolicitud.PENDIENTE);
        Prestador otro = new Prestador("Otro", UUID.randomUUID() + "@mail.com", "hash", tipo);
        otro.setEstadoValidacion(EstadoValidacion.APROBADO);
        otro = prestadores.save(otro);
        Cliente otraCliente = clientes.save(new Cliente("Otra", UUID.randomUUID() + "@mail.com", "hash"));

        patchear(s, como(otro.getUuid(), "PRESTADOR"), "{\"accion\":\"ACEPTAR\"}").andExpect(status().isNotFound());
        patchear(s, como(otraCliente.getUuid(), "CLIENTE"), "{\"accion\":\"CANCELAR\"}").andExpect(status().isNotFound());
        patchear(s, como(UUID.randomUUID(), "GESTOR"), "{\"accion\":\"ACEPTAR\"}").andExpect(status().isForbidden());
        mvc.perform(patch(API + "/solicitudes/" + UUID.randomUUID() + "/estado").contextPath(API)
                .contentType("application/json").content("{\"accion\":\"ACEPTAR\"}").with(comoBeto()))
            .andExpect(status().isNotFound());
    }

    @Test
    void extrasNoPermitidosYCuerpoInvalidoSon400() throws Exception {
        Solicitud s = solicitud(EstadoSolicitud.PENDIENTE);
        patchear(s, comoBeto(), "{\"accion\":\"RECHAZAR\",\"precioAcordado\":1000}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("precioAcordado"))
            .andExpect(jsonPath("$.errores[0].detalle").value("solo se puede indicar al aceptar"));
        patchear(s, comoBeto(), "{\"accion\":\"ACEPTAR\",\"motivo\":\"x\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("motivo"))
            .andExpect(jsonPath("$.errores[0].detalle").value("solo se puede indicar al rechazar o cancelar"));
        patchear(s, comoBeto(), "{\"accion\":\"ACEPTAR\",\"precioAcordado\":-1}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("precioAcordado"));
        patchear(s, comoBeto(), "{\"accion\":\"RECHAZAR\",\"motivo\":\"" + "x".repeat(256) + "\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("motivo"));
        patchear(s, comoBeto(), "{}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("accion"));
        assertThat(estadoDe(s)).isEqualTo("PENDIENTE");
    }

    @Test
    void aceptarYCancelarAlMismoTiempoDejanUnoEn200YElOtroEn409() throws Exception {
        Solicitud s = solicitud(EstadoSolicitud.PENDIENTE);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (Connection candado = dataSource.getConnection()) {
            // Una transacción aparte retiene la fila: las dos peticiones leen PENDIENTE y quedan esperando en el UPDATE.
            candado.setAutoCommit(false);
            try (PreparedStatement ps = candado.prepareStatement(
                    "UPDATE solicitudes_servicio SET descripcion = descripcion WHERE uuid = ?")) {
                ps.setObject(1, s.getUuid());
                ps.executeUpdate();
            }

            Callable<MockHttpServletResponse> aceptar = () -> patchear(s, comoBeto(), "{\"accion\":\"ACEPTAR\"}")
                .andReturn().getResponse();
            Callable<MockHttpServletResponse> cancelar = () -> patchear(s, comoAna(), "{\"accion\":\"CANCELAR\"}")
                .andReturn().getResponse();
            Future<MockHttpServletResponse> a = pool.submit(aceptar);
            Future<MockHttpServletResponse> c = pool.submit(cancelar);

            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' "
                + "AND query ILIKE 'update solicitudes_servicio%'", Integer.class) < 2) {
                assertThat(System.nanoTime()).as("las dos peticiones llegan al UPDATE").isLessThan(limite);
                Thread.sleep(50);
            }
            candado.commit();

            MockHttpServletResponse respAceptar = a.get(20, TimeUnit.SECONDS);
            MockHttpServletResponse respCancelar = c.get(20, TimeUnit.SECONDS);
            assertThat(List.of(respAceptar.getStatus(), respCancelar.getStatus())).containsExactlyInAnyOrder(200, 409);

            boolean ganoAceptar = respAceptar.getStatus() == 200;
            MockHttpServletResponse perdedora = ganoAceptar ? respCancelar : respAceptar;
            JsonNode error = new ObjectMapper().readTree(perdedora.getContentAsString(StandardCharsets.UTF_8));
            assertThat(error.get("codigo").asText()).isEqualTo("TRANSICION_INVALIDA");
            assertThat(error.get("mensaje").asText()).isEqualTo("La solicitud cambió mientras tanto. Recargala.");

            assertThat(estadoDe(s)).isEqualTo(ganoAceptar ? "ACEPTADA" : "CANCELADA");
            // un solo aviso, para la contraparte de quien ganó: si aceptó Beto, Ana; si canceló Ana, Beto
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE uuid_solicitud = ?", Integer.class,
                s.getUuid())).isEqualTo(1);
            assertThat(avisosDe(ganoAceptar ? "CLIENTE" : "PRESTADOR", ganoAceptar ? ana.getId() : beto.getId()))
                .hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private String estadoDe(Solicitud s) {
        return jdbc.queryForObject("SELECT estado FROM solicitudes_servicio WHERE uuid = ?", String.class, s.getUuid());
    }
}
