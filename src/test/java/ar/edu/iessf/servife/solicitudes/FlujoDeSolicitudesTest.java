package ar.edu.iessf.servife.solicitudes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.edu.iessf.servife.identidad.domain.Gestor;
import ar.edu.iessf.servife.identidad.repository.GestorRepository;

/**
 * Flujo completo de solicitudes por HTTP contra PostgreSQL real, con tokens de A2 (cadena de seguridad
 * real): prestador aprobado por el gestor, búsqueda, imagen, solicitud, aceptación, inicio, fin y
 * una segunda solicitud cancelada por el cliente.
 */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class FlujoDeSolicitudesTest {

    private static final String API = "/api/v1";
    private static final String CONTRASENIA = "Clave1234";
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
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

    /** Reloj que arranca en la hora real (el JwtDecoder valida contra el reloj del sistema) y se puede adelantar. */
    static class RelojMovil extends Clock {
        volatile Instant ahora = Instant.now();

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
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
    @Autowired private GestorRepository gestores;
    @Autowired private PasswordEncoder codificador;

    private JsonNode llamar(MockHttpServletRequestBuilder pedido, String token, int estado, Object cuerpo)
            throws Exception {
        pedido.contextPath(API);
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        if (cuerpo != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        }
        MvcResult r = mvc.perform(pedido).andExpect(status().is(estado)).andReturn();
        String texto = r.getResponse().getContentAsString();
        return texto.isEmpty() ? null : json.readTree(texto);
    }

    private String login(String email) throws Exception {
        return llamar(post(API + "/auth/login"), null, 200,
            Map.of("email", email, "contrasenia", CONTRASENIA)).get("accessToken").asText();
    }

    private List<String> tiposDeAvisos(String token) throws Exception {
        List<String> tipos = new ArrayList<>();
        for (JsonNode a : llamar(get(API + "/notificaciones"), token, 200, null).get("contenido")) {
            tipos.add(a.get("tipo").asText());
        }
        return tipos;
    }

    private JsonNode cambiarEstado(String uuid, String token, Map<String, Object> cuerpo) throws Exception {
        return llamar(patch(API + "/solicitudes/" + uuid + "/estado"), token, 200, cuerpo);
    }

    private Map<String, Object> pedido(String prestador, LocalDate fecha, List<String> imagenes, String descripcion) {
        return Map.of("uuidPrestador", prestador, "fechaDeseada", fecha.toString(), "horaPreferida", "mañana",
            "direccion", "Calle 1 123", "descripcion", descripcion, "imagenIds", imagenes);
    }

    @Test
    void flujoCompletoDeSolicitudes() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String emailPrestador = "prest-" + sufijo + "@mail.com";
        String emailCliente = "cli-" + sufijo + "@mail.com";
        String emailGestor = "gestor-" + sufijo + "@mail.com";

        // Gestor creado por repositorio (el inicial solo sale de variables de entorno)
        gestores.save(new Gestor("Gina Gestora", emailGestor, codificador.encode(CONTRASENIA)));
        String tokenGestor = login(emailGestor);

        // B1 + A1: registro de prestador (Gas) y de cliente
        String idGas = null;
        for (JsonNode t : llamar(get(API + "/tipos-servicio"), null, 200, null)) {
            if ("Gas".equals(t.get("nombre").asText())) {
                idGas = t.get("uuid").asText();
            }
        }
        assertThat(idGas).isNotNull();
        String uuidPrestador = llamar(post(API + "/auth/registro"), null, 201, Map.of("rol", "PRESTADOR",
            "nombreApellido", "Pedro Gasista", "email", emailPrestador, "contrasenia", CONTRASENIA,
            "idTipoServicio", idGas)).get("uuid").asText();
        llamar(post(API + "/auth/registro"), null, 201, Map.of("rol", "CLIENTE", "nombreApellido", "Ana Cliente",
            "email", emailCliente, "contrasenia", CONTRASENIA));
        String tokenPrestador = login(emailPrestador);
        String tokenCliente = login(emailCliente);

        // E6: el gestor aprueba al prestador
        JsonNode validacion = llamar(patch(API + "/admin/prestadores/" + uuidPrestador + "/validacion"),
            tokenGestor, 200, Map.of("decision", "APROBAR"));
        assertThat(validacion.get("estadoValidacion").asText()).isEqualTo("APROBADO");

        // B7 + B8: perfil y disponibilidad (todos los días)
        llamar(put(API + "/prestadores/me/perfil"), tokenPrestador, 200, Map.of("idTipoServicio", idGas,
            "zona", "Santa Fe Capital", "lat", -31.63, "lng", -60.70, "radioKm", 20,
            "descripcion", "Instalaciones de gas"));
        llamar(put(API + "/prestadores/me/disponibilidad"), tokenPrestador, 200,
            Map.of("dias", List.of(1, 2, 3, 4, 5, 6, 7)));

        // B5: el cliente encuentra al prestador
        boolean encontrado = false;
        for (JsonNode p : llamar(get(API + "/prestadores").queryParam("tipoServicioId", idGas), tokenCliente, 200,
                null).get("contenido")) {
            encontrado |= uuidPrestador.equals(p.get("uuid").asText());
        }
        assertThat(encontrado).as("prestador en la búsqueda").isTrue();

        // D7: el cliente sube una imagen
        MvcResult subida = mvc.perform(multipart(API + "/archivos")
                .file(new MockMultipartFile("archivo", "f.png", "image/png", PNG))
                .contextPath(API).header("Authorization", "Bearer " + tokenCliente))
            .andExpect(status().isCreated()).andReturn();
        String uuidImagen = json.readTree(subida.getResponse().getContentAsString()).get("uuid").asText();

        // C1: solicitud para mañana (Argentina)
        LocalDate fecha = LocalDate.ofInstant(reloj.instant(), ARGENTINA).plusDays(1);
        JsonNode creada = llamar(post(API + "/solicitudes"), tokenCliente, 201,
            pedido(uuidPrestador, fecha, List.of(uuidImagen), "Pérdida de gas en la cocina"));
        String uuidSolicitud = creada.get("uuid").asText();
        assertThat(creada.get("estado").asText()).isEqualTo("PENDIENTE");

        // El prestador ve el aviso, la solicitud en C2 y puede leer la imagen
        assertThat(tiposDeAvisos(tokenPrestador)).containsExactlyInAnyOrder("PERFIL_APROBADO", "SOLICITUD_NUEVA");
        JsonNode listaPrestador = llamar(get(API + "/solicitudes"), tokenPrestador, 200, null).get("contenido");
        assertThat(listaPrestador).hasSize(1);
        assertThat(listaPrestador.get(0).get("uuid").asText()).isEqualTo(uuidSolicitud);
        MvcResult imagen = mvc.perform(get(API + "/archivos/" + uuidImagen).contextPath(API)
                .header("Authorization", "Bearer " + tokenPrestador))
            .andExpect(status().isOk()).andReturn();
        assertThat(imagen.getResponse().getContentAsByteArray()).isEqualTo(PNG);

        // C4: ACEPTAR con precio; el cliente lo ve en un aviso
        JsonNode aceptada = cambiarEstado(uuidSolicitud, tokenPrestador,
            Map.of("accion", "ACEPTAR", "precioAcordado", 1500000));
        assertThat(aceptada.get("estado").asText()).isEqualTo("ACEPTADA");
        assertThat(aceptada.get("precioAcordado").asLong()).isEqualTo(1500000L);
        assertThat(tiposDeAvisos(tokenCliente)).containsExactly("SOLICITUD_ACEPTADA");

        // INICIAR solo desde el día deseado: se mueve el reloj y se renueva la sesión
        reloj.ahora = reloj.ahora.plus(Duration.ofDays(1));
        String tokenPrestadorHoy = login(emailPrestador);
        String tokenClienteHoy = login(emailCliente);
        assertThat(cambiarEstado(uuidSolicitud, tokenPrestadorHoy, Map.of("accion", "INICIAR"))
            .get("estado").asText()).isEqualTo("EN_CURSO");
        assertThat(cambiarEstado(uuidSolicitud, tokenPrestadorHoy, Map.of("accion", "FINALIZAR"))
            .get("estado").asText()).isEqualTo("FINALIZADA");
        assertThat(tiposDeAvisos(tokenClienteHoy)).containsExactlyInAnyOrder(
            "SOLICITUD_ACEPTADA", "SOLICITUD_EN_CURSO", "SOLICITUD_FINALIZADA");

        // B6: un servicio realizado
        JsonNode ficha = llamar(get(API + "/prestadores/" + uuidPrestador), tokenClienteHoy, 200, null);
        assertThat(ficha.get("serviciosRealizados").asLong()).isEqualTo(1);

        // Otra solicitud: el cliente la cancela con motivo
        LocalDate otraFecha = LocalDate.ofInstant(reloj.instant(), ARGENTINA).plusDays(1);
        String uuidOtra = llamar(post(API + "/solicitudes"), tokenClienteHoy, 201,
            pedido(uuidPrestador, otraFecha, List.of(), "Revisión de la estufa")).get("uuid").asText();
        JsonNode cancelada = cambiarEstado(uuidOtra, tokenClienteHoy,
            Map.of("accion", "CANCELAR", "motivo", "Ya lo resolví"));
        assertThat(cancelada.get("estado").asText()).isEqualTo("CANCELADA");
        assertThat(cancelada.get("canceladaPor").asText()).isEqualTo("CLIENTE");
        assertThat(cancelada.get("motivo").asText()).isEqualTo("Ya lo resolví");
        assertThat(tiposDeAvisos(tokenPrestadorHoy)).contains("SOLICITUD_CANCELADA");
        assertThat(llamar(get(API + "/prestadores/" + uuidPrestador), tokenClienteHoy, 200, null)
            .get("serviciosRealizados").asLong()).isEqualTo(1);
    }
}
