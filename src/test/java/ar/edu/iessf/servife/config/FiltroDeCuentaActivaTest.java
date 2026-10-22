package ar.edu.iessf.servife.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

/** FiltroDeCuentaActiva con la base real: una cuenta suspendida o dada de baja pierde la sesión en el acto. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class FiltroDeCuentaActivaTest {

    private static final String API = "/api/v1";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private static JwtRequestPostProcessor como(UUID uuid, String rol) {
        return jwt().jwt(j -> j.subject(uuid.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private UUID cliente(String estado, boolean baja) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("INSERT INTO clientes (uuid, nombre_apellido, email, contrasenia, estado_cuenta, eliminado_en)"
            + " VALUES (?, 'Cliente', ?, 'hash', ?, CASE WHEN ? THEN now() END)", uuid, uuid + "@mail.com", estado, baja);
        return uuid;
    }

    @Test
    void unaCuentaActivaPasa() throws Exception {
        mvc.perform(get(API + "/notificaciones/no-leidas").contextPath(API).with(como(cliente("ACTIVA", false), "CLIENTE")))
            .andExpect(status().isOk());
    }

    @Test
    void unaCuentaSuspendidaRecibe403CuentaSuspendida() throws Exception {
        mvc.perform(get(API + "/notificaciones/no-leidas").contextPath(API).with(como(cliente("SUSPENDIDA", false), "CLIENTE")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("CUENTA_SUSPENDIDA"))
            .andExpect(jsonPath("$.mensaje").value("Tu cuenta está suspendida."));
    }

    @Test
    void unaCuentaDadaDeBajaOInexistenteRecibe401() throws Exception {
        for (UUID uuid : new UUID[] {cliente("ACTIVA", true), UUID.randomUUID()}) {
            mvc.perform(get(API + "/notificaciones/no-leidas").contextPath(API).with(como(uuid, "CLIENTE")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        }
    }

    @Test
    void elRolDelTokenTieneQueCoincidirConLaTablaDeLaCuenta() throws Exception {
        mvc.perform(get(API + "/notificaciones/no-leidas").contextPath(API).with(como(cliente("ACTIVA", false), "PRESTADOR")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void lasRutasPublicasNoPasanPorElFiltro() throws Exception {
        mvc.perform(get(API + "/tipos-servicio").contextPath(API)).andExpect(status().isOk());
    }
}
