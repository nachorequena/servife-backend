package ar.edu.iessf.servife.reputacion;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import ar.edu.iessf.servife.solicitudes.repository.SolicitudRepository;

/** D7 (POST /archivos) y GET /archivos/{uuid} por HTTP, con el límite de tamaño de multipart activo. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ArchivoControllerTest {

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
    @Autowired private JdbcTemplate jdbc;

    private static JwtRequestPostProcessor como(UUID uuid, String rol) {
        return jwt().jwt(j -> j.subject(uuid.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private Cliente cliente() {
        return clientes.save(new Cliente("Ana", UUID.randomUUID() + "@mail.com", "hash"));
    }

    private UUID subir(Cliente quien, byte[] datos) throws Exception {
        String cuerpo = mvc.perform(multipart(API + "/archivos")
                .file(new MockMultipartFile("archivo", "f.png", "image/png", datos))
                .contextPath(API).with(como(quien.getUuid(), "CLIENTE")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(cuerpo).get("uuid").asText());
    }

    @Test
    void sinTokenEs401() throws Exception {
        mvc.perform(multipart(API + "/archivos")
                .file(new MockMultipartFile("archivo", "f.png", "image/png", PNG)).contextPath(API))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void pngValidoEs201ConLocationYBody() throws Exception {
        Cliente ana = cliente();
        mvc.perform(multipart(API + "/archivos")
                .file(new MockMultipartFile("archivo", "f.png", "image/png", PNG))
                .contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern(".*/archivos/[0-9a-f-]{36}")))
            .andExpect(jsonPath("$.uuid").exists())
            .andExpect(jsonPath("$.mime").value("image/png"))
            .andExpect(jsonPath("$.bytes").value(PNG.length));
    }

    @Test
    void textoRenombradoJpgEs400ConCampoArchivo() throws Exception {
        Cliente ana = cliente();
        mvc.perform(multipart(API + "/archivos")
                .file(new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", "hola".getBytes(StandardCharsets.UTF_8)))
                .contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("archivo"));
    }

    @Test
    void seisMegabytesEs413() throws Exception {
        Cliente ana = cliente();
        byte[] grande = new byte[6 * 1024 * 1024];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);
        mvc.perform(multipart(API + "/archivos")
                .file(new MockMultipartFile("archivo", "g.png", "image/png", grande))
                .contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.codigo").value("ARCHIVO_DEMASIADO_GRANDE"));
    }

    @Test
    void getDelDuenioEs200ConContentTypeYCachePrivado() throws Exception {
        Cliente ana = cliente();
        UUID archivo = subir(ana, PNG);

        mvc.perform(get(API + "/archivos/" + archivo).contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Cache-Control", "max-age=3600, private"))
            .andExpect(content().bytes(PNG));
    }

    @Test
    void getAjenoEs404() throws Exception {
        Cliente ana = cliente();
        Cliente intruso = cliente();
        UUID archivo = subir(ana, PNG);

        mvc.perform(get(API + "/archivos/" + archivo).contextPath(API).with(como(intruso.getUuid(), "CLIENTE")))
            .andExpect(status().isNotFound());
    }

    @Test
    void getDelPrestadorDeUnaSolicitudQueLoIncluyeEs200() throws Exception {
        Cliente ana = cliente();
        TipoServicio tipo = tiposServicio.findByEliminadoEnIsNullOrderByNombre().get(0);
        Prestador beto = prestadores.save(new Prestador("Beto", UUID.randomUUID() + "@mail.com", "hash", tipo));
        UUID archivo = subir(ana, PNG);
        mvc.perform(get(API + "/archivos/" + archivo).contextPath(API).with(como(beto.getUuid(), "PRESTADOR")))
            .andExpect(status().isNotFound());

        Solicitud s = solicitudes.save(new Solicitud(ana, beto, tipo, "Pérdida", LocalDate.of(2026, 10, 20), null, null));
        jdbc.update("INSERT INTO solicitud_imagenes (id_solicitud, id_archivo) SELECT ?, id_archivo FROM archivos WHERE uuid = ?",
            s.getId(), archivo);

        mvc.perform(get(API + "/archivos/" + archivo).contextPath(API).with(como(beto.getUuid(), "PRESTADOR")))
            .andExpect(status().isOk())
            .andExpect(content().bytes(PNG));
    }
}
