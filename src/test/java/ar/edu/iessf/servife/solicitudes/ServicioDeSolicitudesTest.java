package ar.edu.iessf.servife.solicitudes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import ar.edu.iessf.servife.catalogo.domain.Disponibilidad;
import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.DisponibilidadRepository;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;

/** C1 (POST /solicitudes) de punta a punta contra PostgreSQL real, con un reloj que el test mueve. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ServicioDeSolicitudesTest {

    private static final String API = "/api/v1";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    /** Jueves 8/10/2026 a las 12:00 en Argentina. */
    private static final Instant JUEVES_MEDIODIA_ART = Instant.parse("2026-10-08T15:00:00Z");

    @TempDir
    static Path directorio;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("servife.archivos.directorio", () -> directorio.toString());
    }

    /** Reloj que el test puede mover; se impone al del sistema. */
    static class RelojMovil extends Clock {
        volatile Instant ahora = JUEVES_MEDIODIA_ART;

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        /** Devuelve un reloj fijo en el instante actual (no sigue los cambios posteriores de "ahora"). */
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(ahora, zone); }
        @Override public Instant instant() { return ahora; }
    }

    @TestConfiguration
    static class RelojDePruebaConfig {
        @Bean
        @Primary
        RelojMovil relojMovil() {
            return new RelojMovil();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private RelojMovil reloj;
    @Autowired private ClienteRepository clientes;
    @Autowired private PrestadorRepository prestadores;
    @Autowired private TipoServicioRepository tiposServicio;
    @Autowired private DisponibilidadRepository disponibilidad;
    @Autowired private JdbcTemplate jdbc;

    private Cliente ana;
    private Prestador beto;

    @BeforeEach
    void preparar() {
        reloj.ahora = JUEVES_MEDIODIA_ART;
        ana = new Cliente("Ana Pérez", UUID.randomUUID() + "@mail.com", "hash");
        ana.setTelefono("1111");
        ana = clientes.save(ana);
        beto = prestadorAprobado();
    }

    private Prestador prestadorAprobado() {
        TipoServicio tipo = tiposServicio.findByEliminadoEnIsNullOrderByNombre().get(0);
        Prestador p = new Prestador("Beto Gas", UUID.randomUUID() + "@mail.com", "hash", tipo);
        p.setTelefono("2222");
        p.setEstadoValidacion(EstadoValidacion.APROBADO);
        p = prestadores.save(p);
        disponibilidad.save(new Disponibilidad(p, 4));
        disponibilidad.save(new Disponibilidad(p, 5));
        return p;
    }

    private static JwtRequestPostProcessor como(UUID uuid, String rol) {
        return jwt().jwt(j -> j.subject(uuid.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private UUID subir(Cliente quien) throws Exception {
        String cuerpo = mvc.perform(multipart(API + "/archivos")
                .file(new MockMultipartFile("archivo", "f.png", "image/png", PNG))
                .contextPath(API).with(como(quien.getUuid(), "CLIENTE")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(cuerpo).get("uuid").asText());
    }

    private Map<String, Object> cuerpo(UUID prestador, String fecha, List<UUID> imagenes) {
        return Map.of("uuidPrestador", prestador, "fechaDeseada", fecha, "horaPreferida", "  a la tarde ",
            "direccion", " Calle 1 123 ", "descripcion", " Pérdida en la cocina ", "imagenIds", imagenes);
    }

    private ResultActions crear(Cliente quien, Map<String, Object> datos) throws Exception {
        return mvc.perform(post(API + "/solicitudes").contextPath(API).with(como(quien.getUuid(), "CLIENTE"))
            .contentType("application/json").content(json.writeValueAsString(datos)));
    }

    @Test
    void casoFelizConDosImagenesEs201ConLocationYRespuestaCompleta() throws Exception {
        UUID i1 = subir(ana);
        UUID i2 = subir(ana);

        crear(ana, cuerpo(beto.getUuid(), "2026-10-09", List.of(i1, i2)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("http://localhost/api/v1/solicitudes/[0-9a-f-]{36}")))
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.fechaDeseada").value("2026-10-09"))
            .andExpect(jsonPath("$.horaPreferida").value("a la tarde"))
            .andExpect(jsonPath("$.direccion").value("Calle 1 123"))
            .andExpect(jsonPath("$.descripcion").value("Pérdida en la cocina"))
            .andExpect(jsonPath("$.cliente.uuid").value(ana.getUuid().toString()))
            .andExpect(jsonPath("$.cliente.nombreApellido").value("Ana Pérez"))
            .andExpect(jsonPath("$.cliente.telefono").value(nullValue()))
            .andExpect(jsonPath("$.prestador.uuid").value(beto.getUuid().toString()))
            .andExpect(jsonPath("$.prestador.telefono").value(nullValue()))
            .andExpect(jsonPath("$.tipoServicio.uuid").value(beto.getTipoServicio().getUuid().toString()))
            .andExpect(jsonPath("$.imagenIds", hasSize(2)))
            .andExpect(jsonPath("$.accionesDisponibles", hasSize(1)))
            .andExpect(jsonPath("$.accionesDisponibles[0]").value("CANCELAR"));

        Integer filas = jdbc.queryForObject("SELECT count(*) FROM solicitud_imagenes si JOIN archivos a ON a.id_archivo = si.id_archivo WHERE a.uuid IN (?, ?)",
            Integer.class, i1, i2);
        assertThat(filas).isEqualTo(2);
        // el prestador ya puede ver la imagen
        mvc.perform(get(API + "/archivos/" + i1).contextPath(API).with(como(beto.getUuid(), "PRESTADOR")))
            .andExpect(status().isOk());
    }

    @Test
    void lasImagenesSalenEnElMismoOrdenQueEnElDetalleSinImportarElOrdenEnviado() throws Exception {
        UUID i1 = subir(ana);
        UUID i2 = subir(ana);

        String creada = crear(ana, cuerpo(beto.getUuid(), "2026-10-09", List.of(i2, i1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.imagenIds[0]").value(i1.toString()))
            .andExpect(jsonPath("$.imagenIds[1]").value(i2.toString()))
            .andReturn().getResponse().getContentAsString();

        String uuid = json.readTree(creada).get("uuid").asText();
        mvc.perform(get(API + "/solicitudes/" + uuid).contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(jsonPath("$.imagenIds[0]").value(i1.toString()))
            .andExpect(jsonPath("$.imagenIds[1]").value(i2.toString()));
    }

    @Test
    void sinImagenesTambienFunciona() throws Exception {
        crear(ana, Map.of("uuidPrestador", beto.getUuid(), "fechaDeseada", "2026-10-09",
            "direccion", "Calle 1", "descripcion", "Algo", "horaPreferida", "   "))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.horaPreferida").value(nullValue()))
            .andExpect(jsonPath("$.imagenIds", hasSize(0)));
    }

    @Test
    void fechaPasadaEs400() throws Exception {
        crear(ana, cuerpo(beto.getUuid(), "2026-10-07", List.of()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("fechaDeseada"))
            .andExpect(jsonPath("$.errores[0].detalle").value("tiene que ser hoy o una fecha futura"));
    }

    @Test
    void hoyALas2230ArgentinasSeAceptaAunqueEnUtcYaSeaManiana() throws Exception {
        reloj.ahora = Instant.parse("2026-10-09T01:30:00Z"); // 22:30 del jueves 8 en Argentina
        crear(ana, cuerpo(beto.getUuid(), "2026-10-08", List.of()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fechaDeseada").value("2026-10-08"));
    }

    @Test
    void diaQueElPrestadorNoTrabajaEs400() throws Exception {
        crear(ana, cuerpo(beto.getUuid(), "2026-10-10", List.of())) // sábado
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("fechaDeseada"))
            .andExpect(jsonPath("$.errores[0].detalle").value("el prestador no trabaja ese día"));
    }

    @Test
    void prestadorPendienteEs400EnUuidPrestador() throws Exception {
        beto.setEstadoValidacion(EstadoValidacion.PENDIENTE);
        prestadores.save(beto);
        crear(ana, cuerpo(beto.getUuid(), "2026-10-09", List.of()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("uuidPrestador"))
            .andExpect(jsonPath("$.errores[0].detalle").value("no está disponible"));
    }

    @Test
    void prestadorInexistenteOSuspendidoEs400() throws Exception {
        crear(ana, cuerpo(UUID.randomUUID(), "2026-10-09", List.of()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("uuidPrestador"));
        beto.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);
        prestadores.save(beto);
        crear(ana, cuerpo(beto.getUuid(), "2026-10-09", List.of()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("uuidPrestador"));
    }

    @Test
    void imagenAjenaEs400EnImagenIds() throws Exception {
        Cliente otro = clientes.save(new Cliente("Otro", UUID.randomUUID() + "@mail.com", "hash"));
        UUID ajena = subir(otro);
        crear(ana, cuerpo(beto.getUuid(), "2026-10-09", List.of(ajena)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("imagenIds"))
            .andExpect(jsonPath("$.errores[0].detalle").value("alguna imagen no es válida"));
    }

    @Test
    void imagenInexistenteOYaUsadaORepetidaEs400() throws Exception {
        crear(ana, cuerpo(beto.getUuid(), "2026-10-09", List.of(UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("imagenIds"));

        UUID usada = subir(ana);
        crear(ana, cuerpo(beto.getUuid(), "2026-10-09", List.of(usada))).andExpect(status().isCreated());
        crear(ana, cuerpo(beto.getUuid(), "2026-10-09", List.of(usada)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("imagenIds"));

        UUID nueva = subir(ana);
        crear(ana, cuerpo(beto.getUuid(), "2026-10-09", List.of(nueva, nueva)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("imagenIds"));
    }

    @Test
    void creaElAvisoParaElPrestador() throws Exception {
        crear(ana, cuerpo(beto.getUuid(), "2026-10-09", List.of())).andExpect(status().isCreated());

        Map<String, Object> aviso = jdbc.queryForMap(
            "SELECT tipo, titulo, cuerpo, uuid_solicitud, rol_usuario FROM notificaciones WHERE id_usuario = ? AND rol_usuario = 'PRESTADOR'",
            beto.getId());
        assertThat(aviso.get("tipo")).isEqualTo("SOLICITUD_NUEVA");
        assertThat(aviso.get("titulo")).isEqualTo("Nueva solicitud");
        assertThat(aviso.get("cuerpo")).isEqualTo("Ana Pérez te pidió un servicio para el 09/10/2026.");
        assertThat(aviso.get("uuid_solicitud")).isNotNull();
    }

    @Test
    void prestadorYGestorNoPuedenCrear() throws Exception {
        for (String rol : List.of("PRESTADOR", "GESTOR")) {
            mvc.perform(post(API + "/solicitudes").contextPath(API).with(como(beto.getUuid(), rol))
                    .contentType("application/json")
                    .content(json.writeValueAsString(cuerpo(beto.getUuid(), "2026-10-09", List.of()))))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void camposObligatoriosFaltantesEs400() throws Exception {
        crear(ana, Map.of("uuidPrestador", beto.getUuid()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void elIndiceUnicoRechazaUnaSegundaVinculacionDeLaImagen() throws Exception {
        UUID imagen = subir(ana);
        crear(ana, cuerpo(beto.getUuid(), "2026-10-09", List.of(imagen))).andExpect(status().isCreated());
        Long otraSolicitud = jdbc.queryForObject("""
            INSERT INTO solicitudes_servicio (id_cliente, id_prestador, id_tipo_servicio, descripcion)
            VALUES (?, ?, (SELECT id_tipo_servicio FROM prestadores WHERE id_prestador = ?), 'x') RETURNING id_solicitud""",
            Long.class, ana.getId(), beto.getId(), beto.getId());

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> jdbc.update("INSERT INTO solicitud_imagenes (id_solicitud, id_archivo) SELECT ?, id_archivo FROM archivos WHERE uuid = ?",
                otraSolicitud, imagen));
    }

    @Test
    void dosSolicitudesConLaMismaImagenAlMismoTiempoUnaGanaYLaOtraEs400() throws Exception {
        UUID imagen = subir(ana);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CyclicBarrier salida = new java.util.concurrent.CyclicBarrier(2);
        try {
            List<java.util.concurrent.Future<Integer>> resultados = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                resultados.add(pool.submit(() -> {
                    salida.await();
                    return crear(ana, cuerpo(beto.getUuid(), "2026-10-09", List.of(imagen)))
                        .andReturn().getResponse().getStatus();
                }));
            }
            List<Integer> estados = new java.util.ArrayList<>();
            for (var f : resultados) {
                estados.add(f.get());
            }
            assertThat(estados).containsExactlyInAnyOrder(201, 400);
        } finally {
            pool.shutdownNow();
        }
    }
}
