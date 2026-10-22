package ar.edu.iessf.servife.gestion;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.edu.iessf.servife.config.JwtConfig;

/**
 * Expediente por HTTP con cuentas reales: el prestador carga (E7) y ve sus metadatos (E7b), el gestor ve el
 * expediente (E8) y los bytes (E8b, sin caché), nadie más los ve, y nunca salen por /archivos.
 */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ExpedienteHttpTest {

    private static final String API = "/api/v1";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 7, 7};

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    private static JwtRequestPostProcessor como(UUID uuid, String rol) {
        return jwt().jwt(j -> j.subject(uuid.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private UUID cuenta(String tabla) {
        UUID uuid = UUID.randomUUID();
        String extra = tabla.equals("prestadores") ? ", id_tipo_servicio" : "";
        String valor = tabla.equals("prestadores") ? ", (SELECT id_tipo_servicio FROM tipos_servicio WHERE nombre = 'Gas')" : "";
        jdbc.update("INSERT INTO " + tabla + " (uuid, nombre_apellido, email, contrasenia" + extra + ") VALUES (?, 'X', ?, 'hash'"
            + valor + ")", uuid, uuid + "@mail.com");
        return uuid;
    }

    @Test
    void elPrestadorCargaYElGestorVeElExpedienteSinQueSalgaPorArchivos() throws Exception {
        UUID prestador = cuenta("prestadores");
        UUID gestor = cuenta("gestores");
        UUID cliente = cuenta("clientes");

        String respuesta = mvc.perform(multipart(API + "/prestadores/me/documentos")
                .file(new MockMultipartFile("archivo", "dni.png", "image/png", PNG))
                .contextPath(API)
                .param("tipo", "DNI_FRENTE").param("declaracionJurada", "true")
                .with(como(prestador, "PRESTADOR")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tipo").value("DNI_FRENTE"))
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.ruta").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        JsonNode documento = json.readTree(respuesta);
        String uuidDocumento = documento.get("uuid").asText();

        mvc.perform(get(API + "/prestadores/me/documentos").contextPath(API).with(como(prestador, "PRESTADOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.declaracionJuradaEn").isNotEmpty())
            .andExpect(jsonPath("$.documentos[0].uuid").value(uuidDocumento));

        String expediente = API + "/admin/prestadores/" + prestador + "/documentos";
        mvc.perform(get(expediente).contextPath(API).with(como(gestor, "GESTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentos[0].tipo").value("DNI_FRENTE"));
        mvc.perform(get(expediente + "/" + uuidDocumento).contextPath(API).with(como(gestor, "GESTOR")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().bytes(PNG));

        // Nadie más: ni el prestador ni un cliente ven los bytes, y /archivos no los conoce.
        mvc.perform(get(expediente + "/" + uuidDocumento).contextPath(API).with(como(prestador, "PRESTADOR")))
            .andExpect(status().isForbidden());
        mvc.perform(get(expediente).contextPath(API).with(como(cliente, "CLIENTE")))
            .andExpect(status().isForbidden());
        mvc.perform(get(API + "/archivos/" + uuidDocumento).contextPath(API).with(como(prestador, "PRESTADOR")))
            .andExpect(status().isNotFound());
    }

    @Test
    void unClienteOUnGestorNoCarganDocumentos() throws Exception {
        for (String[] quien : new String[][] {{"clientes", "CLIENTE"}, {"gestores", "GESTOR"}}) {
            mvc.perform(multipart(API + "/prestadores/me/documentos")
                    .file(new MockMultipartFile("archivo", "dni.png", "image/png", PNG))
                    .contextPath(API)
                    .param("tipo", "DNI_FRENTE").param("declaracionJurada", "true")
                    .with(como(cuenta(quien[0]), quien[1])))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void tipoInvalidoOSinArchivoEs400() throws Exception {
        UUID prestador = cuenta("prestadores");
        mvc.perform(multipart(API + "/prestadores/me/documentos")
                .file(new MockMultipartFile("archivo", "x.png", "image/png", PNG))
                .contextPath(API)
                .param("tipo", "ANTECEDENTES_RNR").param("declaracionJurada", "true")
                .with(como(prestador, "PRESTADOR")))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart(API + "/prestadores/me/documentos").contextPath(API)
                .param("tipo", "DNI_FRENTE").param("declaracionJurada", "true")
                .with(como(prestador, "PRESTADOR")))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart(API + "/prestadores/me/documentos")
                .file(new MockMultipartFile("archivo", "x.png", "image/png", PNG))
                .contextPath(API)
                .param("tipo", "DNI_FRENTE")
                .with(como(prestador, "PRESTADOR")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("declaracionJurada"));
    }

    @Test
    void documentoDeOtroPrestadorOUuidMalFormadoNoSeVen() throws Exception {
        UUID gestor = cuenta("gestores");
        UUID prestador = cuenta("prestadores");
        mvc.perform(get(API + "/admin/prestadores/" + prestador + "/documentos/" + UUID.randomUUID()).contextPath(API)
                .with(como(gestor, "GESTOR")))
            .andExpect(status().isNotFound());
        mvc.perform(get(API + "/admin/prestadores/" + UUID.randomUUID() + "/documentos").contextPath(API)
                .with(como(gestor, "GESTOR")))
            .andExpect(status().isNotFound());
        mvc.perform(get(API + "/admin/prestadores/no-es-uuid/documentos").contextPath(API).with(como(gestor, "GESTOR")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }
}
