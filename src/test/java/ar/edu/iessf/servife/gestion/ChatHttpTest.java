package ar.edu.iessf.servife.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.gestion.service.ServicioDeChats;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Gestor;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.GestorRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import ar.edu.iessf.servife.solicitudes.repository.SolicitudRepository;

/** Chat de la solicitud (E1, E2, E12, D33) por HTTP contra PostgreSQL real. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ChatHttpTest {

    private static final String API = "/api/v1";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private ServicioDeChats servicioDeChats;
    @Autowired private ClienteRepository clientes;
    @Autowired private PrestadorRepository prestadores;
    @Autowired private GestorRepository gestores;
    @Autowired private TipoServicioRepository tiposServicio;
    @Autowired private SolicitudRepository solicitudes;
    @Autowired private JdbcTemplate jdbc;

    private Cliente ana;
    private Prestador beto;
    private Solicitud solicitud;
    private UUID chat;

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

    private Cliente cliente() {
        return clientes.save(new Cliente("Ana Cliente", UUID.randomUUID() + "@mail.com", "hash"));
    }

    /** Una solicitud de ana a beto con su chat, como la deja C1. */
    private Solicitud solicitudConChat() {
        TipoServicio tipo = tiposServicio.findByEliminadoEnIsNullOrderByNombre().get(0);
        Solicitud s = solicitudes.save(new Solicitud(ana, beto, tipo, "Pérdida", LocalDate.of(2026, 12, 1), null, "Calle 1"));
        servicioDeChats.crearPara(s);
        return s;
    }

    private UUID chatDe(Solicitud s) {
        return jdbc.queryForObject("SELECT uuid FROM chats WHERE id_solicitud = ?", UUID.class, s.getId());
    }

    private JsonNode enviar(UUID uuidChat, JwtRequestPostProcessor quien, String texto) throws Exception {
        String cuerpo = mvc.perform(post(API + "/chats/" + uuidChat + "/mensajes").contextPath(API).with(quien)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("texto", texto))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private JsonNode leer(MockHttpServletRequestBuilder pedido, JwtRequestPostProcessor quien) throws Exception {
        return json.readTree(mvc.perform(pedido.contextPath(API).with(quien))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void cambiarEstado(String estado) {
        jdbc.update("UPDATE solicitudes_servicio SET estado = ? WHERE id_solicitud = ?", estado, solicitud.getId());
    }

    @BeforeEach
    void preparar() {
        ana = cliente();
        TipoServicio tipo = tiposServicio.findByEliminadoEnIsNullOrderByNombre().get(0);
        beto = prestadores.save(new Prestador("Beto Prestador", UUID.randomUUID() + "@mail.com", "hash", tipo));
        solicitud = solicitudConChat();
        chat = chatDe(solicitud);
    }

    @Test
    void elClienteEscribeYElPrestadorLeeConLosNoLeidosEnLaBandeja() throws Exception {
        JsonNode enviado = enviar(chat, comoAna(), "  ¿Viene mañana?  ");
        assertThat(enviado.get("texto").asText()).isEqualTo("¿Viene mañana?");
        assertThat(enviado.get("propio").asBoolean()).isTrue();
        enviar(chat, comoAna(), "Es urgente");

        JsonNode bandeja = leer(get(API + "/conversaciones"), comoBeto());
        assertThat(bandeja.get("totalElementos").asLong()).isEqualTo(1);
        JsonNode fila = bandeja.get("contenido").get(0);
        assertThat(fila.get("uuidChat").asText()).isEqualTo(chat.toString());
        assertThat(fila.get("uuidSolicitud").asText()).isEqualTo(solicitud.getUuid().toString());
        assertThat(fila.get("contraparte").get("nombreApellido").asText()).isEqualTo("Ana Cliente");
        assertThat(fila.get("ultimoMensaje").get("texto").asText()).isEqualTo("Es urgente");
        assertThat(fila.get("ultimoMensaje").get("propio").asBoolean()).isFalse();
        assertThat(fila.get("noLeidos").asLong()).isEqualTo(2);

        JsonNode leido = leer(get(API + "/chats/" + chat + "/mensajes"), comoBeto());
        assertThat(leido.get("sePuedeEscribir").asBoolean()).isTrue();
        assertThat(leido.get("estadoSolicitud").asText()).isEqualTo("PENDIENTE");
        assertThat(leido.get("contraparte").get("nombreApellido").asText()).isEqualTo("Ana Cliente");
        assertThat(leido.get("mensajes")).hasSize(2);
        assertThat(leido.get("mensajes").get(0).get("propio").asBoolean()).isFalse();

        // Leer el chat lo deja sin no leídos para el prestador; para la cliente sus propios mensajes nunca cuentan.
        assertThat(leer(get(API + "/conversaciones"), comoBeto()).get("contenido").get(0).get("noLeidos").asLong()).isZero();
        assertThat(leer(get(API + "/conversaciones"), comoAna()).get("contenido").get(0).get("noLeidos").asLong()).isZero();
    }

    @Test
    void conDesdeDevuelveSoloLosMensajesPosteriores() throws Exception {
        enviar(chat, comoAna(), "uno");
        String dos = enviar(chat, comoBeto(), "dos").get("uuid").asText();
        enviar(chat, comoAna(), "tres");

        JsonNode nuevos = leer(get(API + "/chats/" + chat + "/mensajes").queryParam("desde", dos), comoBeto());
        assertThat(nuevos.get("mensajes")).hasSize(1);
        assertThat(nuevos.get("mensajes").get(0).get("texto").asText()).isEqualTo("tres");
    }

    @Test
    void unDesdeDeOtroChatEs400() throws Exception {
        Solicitud otra = solicitudConChat();
        String ajeno = enviar(chatDe(otra), comoAna(), "en otro chat").get("uuid").asText();

        mvc.perform(get(API + "/chats/" + chat + "/mensajes").queryParam("desde", ajeno).contextPath(API).with(comoAna()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("desde"));
    }

    @Test
    void laBandejaOrdenaPorUltimaActividad() throws Exception {
        Solicitud otra = solicitudConChat();
        UUID otroChat = chatDe(otra);
        enviar(chat, comoAna(), "en el primero");

        JsonNode contenido = leer(get(API + "/conversaciones"), comoAna()).get("contenido");
        assertThat(contenido).hasSize(2);
        assertThat(contenido.get(0).get("uuidChat").asText()).isEqualTo(chat.toString());
        assertThat(contenido.get(1).get("uuidChat").asText()).isEqualTo(otroChat.toString());
        assertThat(contenido.get(1).get("ultimoMensaje").isNull()).isTrue();
    }

    @Test
    void quienNoEsParteRecibe404() throws Exception {
        Cliente intruso = cliente();
        JwtRequestPostProcessor comoIntruso = como(intruso.getUuid(), "CLIENTE");

        mvc.perform(get(API + "/chats/" + chat + "/mensajes").contextPath(API).with(comoIntruso))
            .andExpect(status().isNotFound());
        mvc.perform(post(API + "/chats/" + chat + "/mensajes").contextPath(API).with(comoIntruso)
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"hola\"}"))
            .andExpect(status().isNotFound());
        assertThat(leer(get(API + "/conversaciones"), comoIntruso).get("totalElementos").asLong()).isZero();
        // Mismo uuid con otro rol: esa cuenta no existe, y FiltroDeCuentaActiva corta con 401.
        mvc.perform(get(API + "/chats/" + chat + "/mensajes").contextPath(API).with(como(ana.getUuid(), "PRESTADOR")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void elGestorNoTieneChatYSinTokenEs401() throws Exception {
        Gestor gema = gestores.save(new Gestor("Gema", UUID.randomUUID() + "@mail.com", "hash"));
        mvc.perform(get(API + "/chats/" + chat + "/mensajes").contextPath(API).with(como(gema.getUuid(), "GESTOR")))
            .andExpect(status().isForbidden());
        mvc.perform(get(API + "/conversaciones").contextPath(API).with(como(gema.getUuid(), "GESTOR")))
            .andExpect(status().isForbidden());
        mvc.perform(get(API + "/chats/" + chat + "/mensajes").contextPath(API))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/conversaciones").contextPath(API))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validaElTextoYElUuid() throws Exception {
        for (String malo : new String[] {"{\"texto\":\"   \"}", "{}", "{\"texto\":\"" + "a".repeat(1001) + "\"}"}) {
            mvc.perform(post(API + "/chats/" + chat + "/mensajes").contextPath(API).with(comoAna())
                    .contentType(MediaType.APPLICATION_JSON).content(malo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("texto"));
        }
        // 1000 caracteres después de recortar entran.
        enviar(chat, comoAna(), "  " + "a".repeat(1000) + "  ");
        mvc.perform(get(API + "/chats/no-es-un-uuid/mensajes").contextPath(API).with(comoAna()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACEPTADA", "EN_CURSO"})
    void seEscribeConLaSolicitudAbierta(String estado) throws Exception {
        cambiarEstado(estado);
        enviar(chat, comoBeto(), "Voy a las 10");
        assertThat(leer(get(API + "/chats/" + chat + "/mensajes"), comoAna()).get("sePuedeEscribir").asBoolean()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FINALIZADA", "VALORADA", "RECHAZADA", "CANCELADA"})
    void conLaSolicitudCerradaElChatEsDeSoloLectura(String estado) throws Exception {
        enviar(chat, comoAna(), "antes de cerrar");
        cambiarEstado(estado);

        mvc.perform(post(API + "/chats/" + chat + "/mensajes").contextPath(API).with(comoAna())
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"hola\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("CHAT_CERRADO"));
        JsonNode chatCerrado = leer(get(API + "/chats/" + chat + "/mensajes"), comoBeto());
        assertThat(chatCerrado.get("sePuedeEscribir").asBoolean()).isFalse();
        assertThat(chatCerrado.get("mensajes")).hasSize(1);
    }
}
