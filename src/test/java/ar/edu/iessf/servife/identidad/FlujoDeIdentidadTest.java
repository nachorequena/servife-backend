package ar.edu.iessf.servife.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.edu.iessf.servife.common.correo.EnviadorDeCorreos;

/**
 * Flujo completo del módulo A por HTTP contra PostgreSQL real: registro, login, sesión, rotación del
 * refresh, cambio de contraseña (A7), recuperación (A8 + A9) y email duplicado.
 */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class FlujoDeIdentidadTest {

    private static final String API = "/api/v1";
    private static final String CONTRASENIA = "Clave1234";
    private static final String CONTRASENIA_A7 = "OtraClave99";
    private static final String CONTRASENIA_A9 = "NuevaClave77";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private EnviadorDeCorreos correos;

    private JsonNode llamar(MockHttpServletRequestBuilder pedido, int estado, Object cuerpo) throws Exception {
        pedido.contextPath(API);
        if (cuerpo != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        }
        MvcResult r = mvc.perform(pedido).andExpect(status().is(estado)).andReturn();
        String texto = r.getResponse().getContentAsString();
        return texto.isEmpty() ? null : json.readTree(texto);
    }

    private JsonNode enviar(String ruta, int estado, Object cuerpo) throws Exception {
        return llamar(post(API + ruta), estado, cuerpo);
    }

    private JsonNode login(String email, String contrasenia, int estado) throws Exception {
        return enviar("/auth/login", estado, Map.of("email", email, "contrasenia", contrasenia));
    }

    private void refreshRechazado(String refresh) throws Exception {
        assertThat(enviar("/auth/refresh", 401, Map.of("refreshToken", refresh)).get("codigo").asText())
            .isEqualTo("REFRESH_INVALIDO");
    }

    private String capturarCodigo(String email) {
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(correos, timeout(5000)).enviar(eq(email), any(), texto.capture());
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(texto.getValue());
        assertThat(m.find()).as("código de 6 dígitos en el correo").isTrue();
        return m.group(1);
    }

    @Test
    void flujoCompletoDeIdentidad() throws Exception {
        String email = "flujo-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.com";

        // B1: el rubro "Gas" sale del catálogo público
        MvcResult tipos = mvc.perform(get(API + "/tipos-servicio").contextPath(API))
            .andExpect(status().isOk()).andReturn();
        String idGas = null;
        for (JsonNode t : json.readTree(tipos.getResponse().getContentAsString())) {
            if ("Gas".equals(t.get("nombre").asText())) {
                idGas = t.get("uuid").asText();
            }
        }
        assertThat(idGas).as("tipo de servicio Gas").isNotNull();

        // A1: registro de prestador
        JsonNode registrado = enviar("/auth/registro", 201, Map.of("rol", "PRESTADOR", "nombreApellido",
            "Pedro Gasista", "email", email, "contrasenia", CONTRASENIA, "idTipoServicio", idGas));
        assertThat(registrado.get("rol").asText()).isEqualTo("PRESTADOR");
        assertThat(registrado.has("contrasenia")).isFalse();

        // A2 + A4
        JsonNode sesion = login(email, CONTRASENIA, 200);
        String access = sesion.get("accessToken").asText();
        String refresh1 = sesion.get("refreshToken").asText();
        assertThat(sesion.get("rol").asText()).isEqualTo("PRESTADOR");
        mvc.perform(get(API + "/auth/me").contextPath(API).header("Authorization", "Bearer " + access))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.estadoValidacion").value("PENDIENTE"));

        // A3: rota; el viejo ya no sirve
        JsonNode rotado = enviar("/auth/refresh", 200, Map.of("refreshToken", refresh1));
        assertThat(rotado.get("refreshToken").asText()).isNotEqualTo(refresh1);
        refreshRechazado(refresh1);

        // A7: el cambio de contraseña revoca los refresh emitidos antes
        JsonNode sesion2 = login(email, CONTRASENIA, 200);
        String access2 = sesion2.get("accessToken").asText();
        String refreshPrevioA7 = sesion2.get("refreshToken").asText();
        llamar(patch(API + "/usuarios/me/password").header("Authorization", "Bearer " + access2), 204,
            Map.of("contraseniaActual", CONTRASENIA, "contraseniaNueva", CONTRASENIA_A7));
        refreshRechazado(refreshPrevioA7);
        login(email, CONTRASENIA, 401);
        String refreshPrevioA9 = login(email, CONTRASENIA_A7, 200).get("refreshToken").asText();

        // A8 + A9: recuperación con el código capturado del correo
        enviar("/auth/recuperar", 204, Map.of("email", email));
        String codigo = capturarCodigo(email);
        enviar("/auth/recuperar/confirmar", 204,
            Map.of("email", email, "codigo", codigo, "contraseniaNueva", CONTRASENIA_A9));
        refreshRechazado(refreshPrevioA9);
        login(email, CONTRASENIA_A7, 401);
        login(email, CONTRASENIA_A9, 200);

        // Mismo email como cliente: 409
        assertThat(enviar("/auth/registro", 409, Map.of("rol", "CLIENTE", "nombreApellido", "Otra Persona",
            "email", email, "contrasenia", CONTRASENIA)).get("codigo").asText()).isEqualTo("EMAIL_YA_REGISTRADO");
    }
}
