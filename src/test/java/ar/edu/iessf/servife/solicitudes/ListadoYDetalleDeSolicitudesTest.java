package ar.edu.iessf.servife.solicitudes;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
import ar.edu.iessf.servife.solicitudes.repository.SolicitudRepository;

/** C2 (GET /solicitudes) y C3 (GET /solicitudes/{uuid}) contra PostgreSQL real. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ListadoYDetalleDeSolicitudesTest {

    private static final String API = "/api/v1";
    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private ClienteRepository clientes;
    @Autowired private PrestadorRepository prestadores;
    @Autowired private TipoServicioRepository tiposServicio;
    @Autowired private SolicitudRepository solicitudes;
    @Autowired private JdbcTemplate jdbc;

    private Cliente ana;
    private Cliente luis;
    private Prestador beto;
    private Prestador carla;
    private TipoServicio tipo;

    @BeforeEach
    void preparar() {
        tipo = tiposServicio.findByEliminadoEnIsNullOrderByNombre().get(0);
        ana = cliente("Ana Pérez", "1111");
        luis = cliente("Luis Gómez", "3333");
        beto = prestador("Beto Gas", "2222");
        carla = prestador("Carla Luz", "4444");
    }

    private Cliente cliente(String nombre, String tel) {
        Cliente c = new Cliente(nombre, UUID.randomUUID() + "@mail.com", "hash");
        c.setTelefono(tel);
        return clientes.save(c);
    }

    private Prestador prestador(String nombre, String tel) {
        Prestador p = new Prestador(nombre, UUID.randomUUID() + "@mail.com", "hash", tipo);
        p.setTelefono(tel);
        p.setEstadoValidacion(EstadoValidacion.APROBADO);
        return prestadores.save(p);
    }

    /** Crea una solicitud con el creado_en indicado (para controlar el orden). */
    private Solicitud solicitud(Cliente c, Prestador p, EstadoSolicitud estado, String descripcion, Instant creadoEn) {
        Solicitud s = new Solicitud(c, p, tipo, descripcion, LocalDate.of(2026, 10, 9), "mañana", "Calle 1 123");
        s.cambiarEstado(estado);
        s = solicitudes.saveAndFlush(s);
        jdbc.update("UPDATE solicitudes_servicio SET creado_en = ? WHERE id_solicitud = ?",
            java.sql.Timestamp.from(creadoEn), s.getId());
        return s;
    }

    private static JwtRequestPostProcessor como(UUID uuid, String rol) {
        return jwt().jwt(j -> j.subject(uuid.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void elClienteVeSoloLasSuyasConLaContraparteYElPrestadorSoloLasSuyas() throws Exception {
        solicitud(ana, beto, EstadoSolicitud.PENDIENTE, "de Ana a Beto", T0);
        solicitud(luis, carla, EstadoSolicitud.PENDIENTE, "de Luis a Carla", T0.plusSeconds(1));
        solicitud(luis, beto, EstadoSolicitud.PENDIENTE, "de Luis a Beto", T0.plusSeconds(2));

        mvc.perform(get(API + "/solicitudes").contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido", hasSize(1)))
            .andExpect(jsonPath("$.totalElementos").value(1))
            .andExpect(jsonPath("$.contenido[0].descripcion").value("de Ana a Beto"))
            .andExpect(jsonPath("$.contenido[0].contraparte.uuid").value(beto.getUuid().toString()))
            .andExpect(jsonPath("$.contenido[0].contraparte.nombreApellido").value("Beto Gas"))
            .andExpect(jsonPath("$.contenido[0].contraparte.telefono").value(nullValue()))
            .andExpect(jsonPath("$.contenido[0].tipoServicio.uuid").value(tipo.getUuid().toString()))
            .andExpect(jsonPath("$.contenido[0].direccion").value("Calle 1 123"))
            .andExpect(jsonPath("$.contenido[0].fechaDeseada").value("2026-10-09"))
            .andExpect(jsonPath("$.contenido[0].horaPreferida").value("mañana"));

        mvc.perform(get(API + "/solicitudes").contextPath(API).with(como(beto.getUuid(), "PRESTADOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido", hasSize(2)))
            .andExpect(jsonPath("$.contenido[0].contraparte.nombreApellido").value("Luis Gómez"))
            .andExpect(jsonPath("$.contenido[1].contraparte.nombreApellido").value("Ana Pérez"));
    }

    @Test
    void elTelefonoDeLaContraparteApareceDesdeAceptada() throws Exception {
        solicitud(ana, beto, EstadoSolicitud.ACEPTADA, "aceptada", T0);

        mvc.perform(get(API + "/solicitudes").contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(jsonPath("$.contenido[0].contraparte.telefono").value("2222"));
        mvc.perform(get(API + "/solicitudes").contextPath(API).with(como(beto.getUuid(), "PRESTADOR")))
            .andExpect(jsonPath("$.contenido[0].contraparte.telefono").value("1111"));
    }

    @Test
    void filtraPorDosEstadosYElValorInvalidoEs400() throws Exception {
        solicitud(ana, beto, EstadoSolicitud.PENDIENTE, "p", T0);
        solicitud(ana, beto, EstadoSolicitud.ACEPTADA, "a", T0.plusSeconds(1));
        solicitud(ana, beto, EstadoSolicitud.CANCELADA, "c", T0.plusSeconds(2));

        mvc.perform(get(API + "/solicitudes").param("estado", "PENDIENTE").param("estado", "ACEPTADA")
                .contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido", hasSize(2)))
            .andExpect(jsonPath("$.totalElementos").value(2))
            .andExpect(jsonPath("$.contenido[0].estado").value("ACEPTADA"))
            .andExpect(jsonPath("$.contenido[1].estado").value("PENDIENTE"));

        mvc.perform(get(API + "/solicitudes").param("estado", "XXX")
                .contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void ordenaPorMasRecientePrimeroYPagina() throws Exception {
        for (int i = 0; i < 5; i++) {
            solicitud(ana, beto, EstadoSolicitud.PENDIENTE, "n" + i, T0.plusSeconds(i));
        }
        mvc.perform(get(API + "/solicitudes").param("page", "0").param("size", "2")
                .contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(jsonPath("$.contenido", hasSize(2)))
            .andExpect(jsonPath("$.contenido[0].descripcion").value("n4"))
            .andExpect(jsonPath("$.contenido[1].descripcion").value("n3"))
            .andExpect(jsonPath("$.pagina").value(0))
            .andExpect(jsonPath("$.tamanio").value(2))
            .andExpect(jsonPath("$.totalElementos").value(5))
            .andExpect(jsonPath("$.totalPaginas").value(3));
        mvc.perform(get(API + "/solicitudes").param("page", "2").param("size", "2")
                .contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(jsonPath("$.contenido", hasSize(1)))
            .andExpect(jsonPath("$.contenido[0].descripcion").value("n0"));
    }

    @Test
    void laDescripcionSeTruncaA140ConPuntosSuspensivos() throws Exception {
        solicitud(ana, beto, EstadoSolicitud.PENDIENTE, "x".repeat(141), T0);
        solicitud(ana, beto, EstadoSolicitud.PENDIENTE, "y".repeat(140), T0.plusSeconds(1));

        mvc.perform(get(API + "/solicitudes").contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(jsonPath("$.contenido[0].descripcion").value("y".repeat(140)))
            .andExpect(jsonPath("$.contenido[1].descripcion").value("x".repeat(140) + "…"));
    }

    @Test
    void noListaNiMuestraLasEliminadas() throws Exception {
        Solicitud s = solicitud(ana, beto, EstadoSolicitud.PENDIENTE, "baja", T0);
        jdbc.update("UPDATE solicitudes_servicio SET eliminado_en = now() WHERE id_solicitud = ?", s.getId());

        mvc.perform(get(API + "/solicitudes").contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(jsonPath("$.contenido", hasSize(0)));
        mvc.perform(get(API + "/solicitudes/" + s.getUuid()).contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isNotFound());
    }

    @Test
    void gestorRecibe403EnAmbos() throws Exception {
        mvc.perform(get(API + "/solicitudes").contextPath(API).with(como(UUID.randomUUID(), "GESTOR")))
            .andExpect(status().isForbidden());
        mvc.perform(get(API + "/solicitudes/" + UUID.randomUUID()).contextPath(API)
                .with(como(UUID.randomUUID(), "GESTOR")))
            .andExpect(status().isForbidden());
    }

    @Test
    void detalleDeUnaPropiaIncluyeImagenesYAccionesSegunElRol() throws Exception {
        Solicitud s = solicitud(ana, beto, EstadoSolicitud.PENDIENTE, "detalle", T0);
        UUID archivo = UUID.randomUUID();
        jdbc.update("INSERT INTO archivos (uuid, ruta, mime, bytes, id_propietario, rol_propietario) VALUES (?, ?, 'image/png', 3, ?, 'CLIENTE')",
            archivo, archivo.toString(), ana.getId());
        jdbc.update("INSERT INTO solicitud_imagenes (id_solicitud, id_archivo) SELECT ?, id_archivo FROM archivos WHERE uuid = ?",
            s.getId(), archivo);

        mvc.perform(get(API + "/solicitudes/" + s.getUuid()).contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uuid").value(s.getUuid().toString()))
            .andExpect(jsonPath("$.descripcion").value("detalle"))
            .andExpect(jsonPath("$.imagenIds", hasSize(1)))
            .andExpect(jsonPath("$.imagenIds[0]").value(archivo.toString()))
            .andExpect(jsonPath("$.accionesDisponibles", hasSize(1)))
            .andExpect(jsonPath("$.accionesDisponibles[0]").value("CANCELAR"));

        mvc.perform(get(API + "/solicitudes/" + s.getUuid()).contextPath(API).with(como(beto.getUuid(), "PRESTADOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accionesDisponibles", hasSize(2)))
            .andExpect(jsonPath("$.accionesDisponibles[0]").value("ACEPTAR"))
            .andExpect(jsonPath("$.accionesDisponibles[1]").value("RECHAZAR"));
    }

    @Test
    void detalleAjenoOInexistenteEs404() throws Exception {
        Solicitud s = solicitud(ana, beto, EstadoSolicitud.PENDIENTE, "ajena", T0);

        mvc.perform(get(API + "/solicitudes/" + s.getUuid()).contextPath(API).with(como(luis.getUuid(), "CLIENTE")))
            .andExpect(status().isNotFound());
        mvc.perform(get(API + "/solicitudes/" + s.getUuid()).contextPath(API).with(como(carla.getUuid(), "PRESTADOR")))
            .andExpect(status().isNotFound());
        mvc.perform(get(API + "/solicitudes/" + UUID.randomUUID()).contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isNotFound());
    }
}
