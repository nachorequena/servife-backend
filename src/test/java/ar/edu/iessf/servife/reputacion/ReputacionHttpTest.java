package ar.edu.iessf.servife.reputacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.reputacion.domain.Valoracion;
import ar.edu.iessf.servife.reputacion.repository.ValoracionRepository;
import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import ar.edu.iessf.servife.solicitudes.domain.SolicitudDePrueba;
import ar.edu.iessf.servife.solicitudes.repository.SolicitudRepository;

/** D1, D2, D3, D4, D5, D6 y D8 de punta a punta contra PostgreSQL real (reglas de negocio, V8 y consultas). */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReputacionHttpTest {

    private static final String API = "/api/v1";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @TempDir
    static Path directorio;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("servife.archivos.directorio", () -> directorio.toString());
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private ClienteRepository clientes;
    @Autowired private PrestadorRepository prestadores;
    @Autowired private TipoServicioRepository tiposServicio;
    @Autowired private SolicitudRepository solicitudes;
    @Autowired private ValoracionRepository valoraciones;
    @Autowired private JdbcTemplate jdbc;

    private TipoServicio tipo;
    private Cliente ana;
    private Prestador beto;
    private Prestador carla;

    @BeforeEach
    void preparar() {
        tipo = tiposServicio.findByEliminadoEnIsNullOrderByNombre().get(0);
        ana = clientes.save(new Cliente("Ana Pérez", UUID.randomUUID() + "@mail.com", "hash"));
        beto = prestador("Beto Gas", EstadoValidacion.APROBADO);
        carla = prestador("Carla Luz", EstadoValidacion.APROBADO);
    }

    private Prestador prestador(String nombre, EstadoValidacion estado) {
        Prestador p = new Prestador(nombre, UUID.randomUUID() + "@mail.com", "hash", tipo);
        p.setEstadoValidacion(estado);
        return prestadores.save(p);
    }

    private Solicitud solicitud(Cliente c, Prestador p, EstadoSolicitud estado) {
        Solicitud s = new Solicitud(c, p, tipo, "Pérdida de gas", LocalDate.of(2026, 10, 9), null, "Calle 1 123");
        SolicitudDePrueba.enEstado(s, estado);
        return solicitudes.saveAndFlush(s);
    }

    private static JwtRequestPostProcessor como(UUID uuid, String rol) {
        return jwt().jwt(j -> j.subject(uuid.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private UUID subir(UUID quien, String rol) throws Exception {
        String cuerpo = mvc.perform(multipart(API + "/archivos")
                .file(new MockMultipartFile("archivo", "f.png", "image/png", PNG))
                .contextPath(API).with(como(quien, rol)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(cuerpo).get("uuid").asText());
    }

    private static String cuerpo(String titulo, UUID solicitud, UUID... imagenes) {
        StringBuilder b = new StringBuilder("{\"titulo\":\"").append(titulo).append('"')
            .append(",\"descripcion\":\"Trabajo prolijo\",\"fechaTrabajo\":\"2026-10-09\"");
        if (solicitud != null) {
            b.append(",\"uuidSolicitud\":\"").append(solicitud).append('"');
        }
        b.append(",\"imagenIds\":[");
        for (int i = 0; i < imagenes.length; i++) {
            b.append(i == 0 ? "" : ",").append('"').append(imagenes[i]).append('"');
        }
        return b.append("]}").toString();
    }

    private ResultActions publicar(Prestador p, String cuerpo) throws Exception {
        return mvc.perform(post(API + "/publicaciones").contextPath(API).with(como(p.getUuid(), "PRESTADOR"))
            .contentType("application/json").content(cuerpo));
    }

    private UUID publicada(ResultActions resultado) throws Exception {
        String cuerpo = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(cuerpo).get("uuid").asText());
    }

    // ── D4 ────────────────────────────────────────────────────────────────────

    @Test
    void publicaConSolicitudTerminadaEImagenesEnOrden() throws Exception {
        Solicitud s = solicitud(ana, beto, EstadoSolicitud.FINALIZADA);
        UUID img1 = subir(beto.getUuid(), "PRESTADOR");
        UUID img2 = subir(beto.getUuid(), "PRESTADOR");

        publicar(beto, cuerpo("Cambio de cañería", s.getUuid(), img1, img2))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("http://localhost/api/v1/publicaciones/[0-9a-f-]{36}")))
            .andExpect(jsonPath("$.titulo").value("Cambio de cañería"))
            .andExpect(jsonPath("$.descripcion").value("Trabajo prolijo"))
            .andExpect(jsonPath("$.fechaTrabajo").value("2026-10-09"))
            .andExpect(jsonPath("$.uuidSolicitud").value(s.getUuid().toString()))
            .andExpect(jsonPath("$.imagenIds", contains(img1.toString(), img2.toString())))
            .andExpect(jsonPath("$.puntaje").value(nullValue()));
    }

    @Test
    void publicaSinSolicitudNiImagenesYTambienConUnaValorada() throws Exception {
        publicar(beto, "{\"titulo\":\"  Solo el título  \"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.titulo").value("Solo el título"))
            .andExpect(jsonPath("$.descripcion").value(nullValue()))
            .andExpect(jsonPath("$.uuidSolicitud").value(nullValue()))
            .andExpect(jsonPath("$.imagenIds", hasSize(0)));

        Solicitud valorada = solicitud(ana, beto, EstadoSolicitud.VALORADA);
        publicar(beto, cuerpo("Valorada", valorada.getUuid())).andExpect(status().isCreated());
    }

    @Test
    void laSolicitudTieneQueSerPropiaYTerminada() throws Exception {
        Solicitud enCurso = solicitud(ana, beto, EstadoSolicitud.EN_CURSO);
        Solicitud deCarla = solicitud(ana, carla, EstadoSolicitud.FINALIZADA);

        for (UUID invalida : new UUID[] {enCurso.getUuid(), deCarla.getUuid(), UUID.randomUUID()}) {
            publicar(beto, cuerpo("T", invalida))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("uuidSolicitud"))
                .andExpect(jsonPath("$.errores[0].detalle").value("no es un trabajo terminado tuyo"));
        }
    }

    @Test
    void unaSolicitudSePublicaUnaSolaVezMientrasNoSeDeDeBaja() throws Exception {
        Solicitud s = solicitud(ana, beto, EstadoSolicitud.FINALIZADA);
        UUID primera = publicada(publicar(beto, cuerpo("Primera", s.getUuid())));

        publicar(beto, cuerpo("Segunda", s.getUuid()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("uuidSolicitud"))
            .andExpect(jsonPath("$.errores[0].detalle").value("ya tiene una publicación"));

        mvc.perform(delete(API + "/publicaciones/" + primera).contextPath(API).with(como(beto.getUuid(), "PRESTADOR")))
            .andExpect(status().isNoContent());
        publicar(beto, cuerpo("Otra vez", s.getUuid())).andExpect(status().isCreated());
    }

    @Test
    void lasImagenesTienenQueSerPropiasSinRepetirYNoEstarEnOtraPublicacion() throws Exception {
        UUID deAna = subir(ana.getUuid(), "CLIENTE");
        UUID deCarla = subir(carla.getUuid(), "PRESTADOR");
        UUID propia = subir(beto.getUuid(), "PRESTADOR");

        for (String invalido : new String[] {cuerpo("T", null, deAna), cuerpo("T", null, deCarla),
                cuerpo("T", null, propia, propia), cuerpo("T", null, UUID.randomUUID())}) {
            publicar(beto, invalido)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("imagenIds"));
        }

        publicar(beto, cuerpo("Con foto", null, propia)).andExpect(status().isCreated());
        publicar(beto, cuerpo("Misma foto", null, propia))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("imagenIds"));
    }

    // ── D5 y D6 ───────────────────────────────────────────────────────────────

    @Test
    void actualizarReemplazaTodoYConservaLasImagenesQueSiguen() throws Exception {
        Solicitud s = solicitud(ana, beto, EstadoSolicitud.FINALIZADA);
        UUID queda = subir(beto.getUuid(), "PRESTADOR");
        UUID sale = subir(beto.getUuid(), "PRESTADOR");
        UUID entra = subir(beto.getUuid(), "PRESTADOR");
        UUID uuid = publicada(publicar(beto, cuerpo("Antes", s.getUuid(), queda, sale)));

        mvc.perform(put(API + "/publicaciones/" + uuid).contextPath(API).with(como(beto.getUuid(), "PRESTADOR"))
                .contentType("application/json").content("{\"titulo\":\"Después\",\"imagenIds\":[\"" + queda
                    + "\",\"" + entra + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titulo").value("Después"))
            .andExpect(jsonPath("$.descripcion").value(nullValue()))
            .andExpect(jsonPath("$.uuidSolicitud").value(nullValue()))
            .andExpect(jsonPath("$.imagenIds", contains(queda.toString(), entra.toString())));

        // la que salió quedó libre: se puede usar en otra publicación
        publicar(beto, cuerpo("Reusa", null, sale)).andExpect(status().isCreated());
        // y la solicitud desvinculada se puede volver a publicar
        publicar(beto, cuerpo("Con la solicitud", s.getUuid())).andExpect(status().isCreated());
    }

    @Test
    void actualizarPuedeConservarSuPropiaSolicitud() throws Exception {
        Solicitud s = solicitud(ana, beto, EstadoSolicitud.FINALIZADA);
        UUID uuid = publicada(publicar(beto, cuerpo("Antes", s.getUuid())));

        mvc.perform(put(API + "/publicaciones/" + uuid).contextPath(API).with(como(beto.getUuid(), "PRESTADOR"))
                .contentType("application/json").content(cuerpo("Después", s.getUuid())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uuidSolicitud").value(s.getUuid().toString()));
    }

    @Test
    void unaPublicacionAjenaODadaDeBajaEs404() throws Exception {
        UUID deBeto = publicada(publicar(beto, cuerpo("De Beto", null)));

        mvc.perform(put(API + "/publicaciones/" + deBeto).contextPath(API).with(como(carla.getUuid(), "PRESTADOR"))
                .contentType("application/json").content(cuerpo("Mía", null)))
            .andExpect(status().isNotFound());
        mvc.perform(delete(API + "/publicaciones/" + deBeto).contextPath(API).with(como(carla.getUuid(), "PRESTADOR")))
            .andExpect(status().isNotFound());

        mvc.perform(delete(API + "/publicaciones/" + deBeto).contextPath(API).with(como(beto.getUuid(), "PRESTADOR")))
            .andExpect(status().isNoContent());
        mvc.perform(delete(API + "/publicaciones/" + deBeto).contextPath(API).with(como(beto.getUuid(), "PRESTADOR")))
            .andExpect(status().isNotFound());
        mvc.perform(put(API + "/publicaciones/" + deBeto).contextPath(API).with(como(beto.getUuid(), "PRESTADOR"))
                .contentType("application/json").content(cuerpo("Vuelve", null)))
            .andExpect(status().isNotFound());
    }

    // ── D3 y D8 ───────────────────────────────────────────────────────────────

    @Test
    void lasListasMuestranLasEstrellasDeLaSolicitudYSoloAlDuenioSuUuid() throws Exception {
        Solicitud valorada = solicitud(ana, beto, EstadoSolicitud.VALORADA);
        valoraciones.saveAndFlush(new Valoracion(valorada, ana, 4, "Muy bien"));
        Solicitud sinValorar = solicitud(ana, beto, EstadoSolicitud.FINALIZADA);
        UUID img = subir(beto.getUuid(), "PRESTADOR");
        publicada(publicar(beto, cuerpo("Sin solicitud", null)));
        publicada(publicar(beto, cuerpo("Sin valorar", sinValorar.getUuid())));
        publicada(publicar(beto, cuerpo("Valorada", valorada.getUuid(), img)));
        publicada(publicar(carla, cuerpo("De Carla", null)));

        mvc.perform(get(API + "/prestadores/" + beto.getUuid() + "/publicaciones").contextPath(API)
                .with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(3))
            .andExpect(jsonPath("$.contenido[0].titulo").value("Valorada"))
            .andExpect(jsonPath("$.contenido[0].puntaje").value(4))
            .andExpect(jsonPath("$.contenido[0].imagenIds", contains(img.toString())))
            .andExpect(jsonPath("$.contenido[0].uuidSolicitud").value(nullValue()))
            .andExpect(jsonPath("$.contenido[1].titulo").value("Sin valorar"))
            .andExpect(jsonPath("$.contenido[1].puntaje").value(nullValue()))
            .andExpect(jsonPath("$.contenido[2].titulo").value("Sin solicitud"))
            .andExpect(jsonPath("$.contenido[2].puntaje").value(nullValue()));

        mvc.perform(get(API + "/prestadores/me/publicaciones").contextPath(API).with(como(beto.getUuid(), "PRESTADOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(3))
            .andExpect(jsonPath("$.contenido[0].puntaje").value(4))
            .andExpect(jsonPath("$.contenido[0].uuidSolicitud").value(valorada.getUuid().toString()));

        mvc.perform(get(API + "/prestadores/me/publicaciones?size=2&page=1").contextPath(API)
                .with(como(beto.getUuid(), "PRESTADOR")))
            .andExpect(jsonPath("$.contenido", hasSize(1)))
            .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @Test
    void lasDadasDeBajaNoSeListan() throws Exception {
        UUID uuid = publicada(publicar(beto, cuerpo("Borrada", null)));
        mvc.perform(delete(API + "/publicaciones/" + uuid).contextPath(API).with(como(beto.getUuid(), "PRESTADOR")))
            .andExpect(status().isNoContent());

        mvc.perform(get(API + "/prestadores/me/publicaciones").contextPath(API).with(como(beto.getUuid(), "PRESTADOR")))
            .andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(get(API + "/prestadores/" + beto.getUuid() + "/publicaciones").contextPath(API)
                .with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void elPrestadorPendienteGestionaSusTrabajosPeroNadieMasLosVe() throws Exception {
        Prestador nuevo = prestador("Nuevo", EstadoValidacion.PENDIENTE);
        publicada(publicar(nuevo, cuerpo("Mi primer trabajo", null)));

        mvc.perform(get(API + "/prestadores/me/publicaciones").contextPath(API).with(como(nuevo.getUuid(), "PRESTADOR")))
            .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get(API + "/prestadores/" + nuevo.getUuid() + "/publicaciones").contextPath(API)
                .with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isNotFound());
        mvc.perform(get(API + "/prestadores/" + UUID.randomUUID() + "/publicaciones").contextPath(API)
                .with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isNotFound());
    }

    @Test
    void laPaginaFueraDeRangoEs400() throws Exception {
        mvc.perform(get(API + "/prestadores/" + beto.getUuid() + "/publicaciones?size=51").contextPath(API)
                .with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("size"));
    }

    // ── D2 ────────────────────────────────────────────────────────────────────

    @Test
    void lasResenasSonLasDelPrestadorMasRecientesPrimero() throws Exception {
        Cliente luis = clientes.save(new Cliente("Luis Gómez", UUID.randomUUID() + "@mail.com", "hash"));
        Valoracion vieja = valoraciones.saveAndFlush(
            new Valoracion(solicitud(ana, beto, EstadoSolicitud.VALORADA), ana, 5, "Excelente"));
        valoraciones.saveAndFlush(new Valoracion(solicitud(luis, beto, EstadoSolicitud.VALORADA), luis, 3, null));
        valoraciones.saveAndFlush(new Valoracion(solicitud(ana, carla, EstadoSolicitud.VALORADA), ana, 1, "De Carla"));
        jdbc.update("UPDATE valoraciones SET creado_en = ? WHERE id_valoracion = ?",
            java.sql.Timestamp.from(Instant.parse("2026-01-01T10:00:00Z")), vieja.getId());

        mvc.perform(get(API + "/prestadores/" + beto.getUuid() + "/valoraciones").contextPath(API)
                .with(como(luis.getUuid(), "CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(2))
            .andExpect(jsonPath("$.contenido[0].puntaje").value(3))
            .andExpect(jsonPath("$.contenido[0].nombreCliente").value("Luis Gómez"))
            .andExpect(jsonPath("$.contenido[0].comentario").value(nullValue()))
            .andExpect(jsonPath("$.contenido[1].puntaje").value(5))
            .andExpect(jsonPath("$.contenido[1].comentario").value("Excelente"))
            .andExpect(jsonPath("$.contenido[1].nombreCliente").value("Ana Pérez"));
    }

    @Test
    void lasResenasDeUnPrestadorNoVisibleSon404() throws Exception {
        Prestador pendiente = prestador("Pendiente", EstadoValidacion.PENDIENTE);

        mvc.perform(get(API + "/prestadores/" + pendiente.getUuid() + "/valoraciones").contextPath(API)
                .with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isNotFound());
    }

    // ── D1 ────────────────────────────────────────────────────────────────────

    private ResultActions valorar(Cliente c, Solicitud s, String cuerpo) throws Exception {
        return mvc.perform(post(API + "/solicitudes/" + s.getUuid() + "/valoracion").contextPath(API)
            .with(como(c.getUuid(), "CLIENTE")).contentType("application/json").content(cuerpo));
    }

    private String estadoDe(Solicitud s) {
        return jdbc.queryForObject("SELECT estado FROM solicitudes_servicio WHERE id_solicitud = ?", String.class, s.getId());
    }

    private BigDecimal promedioDe(Prestador p) {
        return jdbc.queryForObject("SELECT valoracion_promedio FROM prestadores WHERE id_prestador = ?",
            BigDecimal.class, p.getId());
    }

    @Test
    void valorarGuardaLaResenaPasaAValoradaYRecalculaElPromedio() throws Exception {
        Solicitud primera = solicitud(ana, beto, EstadoSolicitud.FINALIZADA);
        Solicitud segunda = solicitud(ana, beto, EstadoSolicitud.FINALIZADA);

        valorar(ana, primera, "{\"puntaje\":5,\"comentario\":\"  Impecable  \"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.uuid").exists())
            .andExpect(jsonPath("$.puntaje").value(5))
            .andExpect(jsonPath("$.comentario").value("Impecable"))
            .andExpect(jsonPath("$.nombreCliente").value("Ana Pérez"))
            .andExpect(jsonPath("$.creadoEn").exists());
        assertThat(estadoDe(primera)).isEqualTo("VALORADA");
        assertThat(promedioDe(beto)).isEqualByComparingTo("5.0");

        valorar(ana, segunda, "{\"puntaje\":2,\"comentario\":\"   \"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.comentario").value(nullValue()));
        assertThat(promedioDe(beto)).isEqualByComparingTo("3.5");
        assertThat(promedioDe(carla)).isNull();
    }

    @Test
    void valorarDosVecesEs409YNoDuplica() throws Exception {
        Solicitud s = solicitud(ana, beto, EstadoSolicitud.FINALIZADA);
        valorar(ana, s, "{\"puntaje\":4}").andExpect(status().isCreated());

        valorar(ana, s, "{\"puntaje\":1}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        assertThat(promedioDe(beto)).isEqualByComparingTo("4.0");
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM valoraciones WHERE id_solicitud = ?", Long.class, s.getId())).isEqualTo(1L);
    }

    @Test
    void valorarUnaSolicitudQueNoEstaFinalizadaEs409() throws Exception {
        Solicitud enCurso = solicitud(ana, beto, EstadoSolicitud.EN_CURSO);

        valorar(ana, enCurso, "{\"puntaje\":5}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        assertThat(estadoDe(enCurso)).isEqualTo("EN_CURSO");
    }

    @Test
    void valorarUnaSolicitudAjenaEs404() throws Exception {
        Cliente luis = clientes.save(new Cliente("Luis Gómez", UUID.randomUUID() + "@mail.com", "hash"));
        Solicitud deLuis = solicitud(luis, beto, EstadoSolicitud.FINALIZADA);

        valorar(ana, deLuis, "{\"puntaje\":5}").andExpect(status().isNotFound());
        assertThat(estadoDe(deLuis)).isEqualTo("FINALIZADA");
    }
}
