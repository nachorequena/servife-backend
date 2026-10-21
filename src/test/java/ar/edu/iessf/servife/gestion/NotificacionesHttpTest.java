package ar.edu.iessf.servife.gestion;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.gestion.service.Avisos;
import ar.edu.iessf.servife.gestion.service.TipoDeAviso;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;

/** E11 de punta a punta: HTTP, seguridad y PostgreSQL real, sin simular Avisos. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NotificacionesHttpTest {

    private static final String API = "/api/v1";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private Avisos avisos;
    @Autowired private ClienteRepository clientes;

    private static JwtRequestPostProcessor como(UUID uuid, String rol) {
        return jwt().jwt(j -> j.subject(uuid.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private Cliente cliente() {
        return clientes.save(new Cliente("Ana", UUID.randomUUID() + "@mail.com", "hash"));
    }

    @Test
    void listarMarcarLeidaYContarSoloVenLosAvisosPropios() throws Exception {
        Cliente ana = cliente();
        Cliente otra = cliente();
        UUID solicitud = UUID.randomUUID();
        avisos.avisar(ana, TipoDeAviso.SOLICITUD_ACEPTADA, "Aceptaron tu solicitud", "Precio acordado", solicitud);
        avisos.avisar(otra, TipoDeAviso.SOLICITUD_NUEVA, "Nueva solicitud", null, null);

        String cuerpo = mvc.perform(get(API + "/notificaciones").contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(1))
            .andExpect(jsonPath("$.contenido[0].tipo").value("SOLICITUD_ACEPTADA"))
            .andExpect(jsonPath("$.contenido[0].uuidSolicitud").value(solicitud.toString()))
            .andExpect(jsonPath("$.contenido[0].leida").value(false))
            .andReturn().getResponse().getContentAsString();
        String aviso = json.readTree(cuerpo).at("/contenido/0/uuid").asText();

        mvc.perform(get(API + "/notificaciones/no-leidas").contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cantidad").value(1));

        // Un aviso ajeno da 404, aunque exista.
        mvc.perform(patch(API + "/notificaciones/" + aviso + "/leida").contextPath(API).with(como(otra.getUuid(), "CLIENTE")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

        mvc.perform(patch(API + "/notificaciones/" + aviso + "/leida").contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(status().isNoContent());

        mvc.perform(get(API + "/notificaciones/no-leidas").contextPath(API).with(como(ana.getUuid(), "CLIENTE")))
            .andExpect(jsonPath("$.cantidad").value(0));
        mvc.perform(get(API + "/notificaciones/no-leidas").contextPath(API).with(como(otra.getUuid(), "CLIENTE")))
            .andExpect(jsonPath("$.cantidad").value(1));
    }

    @Test
    void elMismoIdEnOtroRolNoVeLosAvisos() throws Exception {
        Cliente ana = cliente();
        avisos.avisar(ana, TipoDeAviso.SOLICITUD_NUEVA, "Nueva solicitud", null, null);

        // El token dice PRESTADOR con el uuid de un cliente: no existe esa cuenta, 404.
        mvc.perform(get(API + "/notificaciones").contextPath(API).with(como(ana.getUuid(), "PRESTADOR")))
            .andExpect(status().isNotFound());
    }
}
