package ar.edu.iessf.servife.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.iessf.servife.catalogo.controller.TipoServicioController;
import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.gestion.controller.AdminUsuarioController;
import ar.edu.iessf.servife.identidad.controller.AuthController;

/**
 * Base transversal: autenticación, roles y formato único de error (.ai/05, .ai/07).
 * No prueba lógica de ningún módulo: cada dueño escribe los tests de sus endpoints.
 */
@WebMvcTest(controllers = {TipoServicioController.class, AdminUsuarioController.class, AuthController.class})
@Import({SeguridadConfig.class, JwtConfig.class, CorsConfig.class, EscritorDeErrores.class})
@TestPropertySource(properties = {
    "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres",
    "servife.jwt.duracion-access=15m",
    "servife.jwt.duracion-refresh=7d",
    "servife.cors.origenes-permitidos=http://localhost:8081"
})
class SeguridadYErroresTest {

    private static final String CONTEXTO = "/api/v1";

    @Autowired
    private MockMvc mvc;

    private static JwtRequestPostProcessor como(String rol) {
        return jwt()
            .jwt(j -> j.subject("7f1c2d4e-0000-4000-8000-000000000001").claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void sinTokenDevuelve401ConElFormatoDeError() throws Exception {
        mvc.perform(get(CONTEXTO + "/tipos-servicio").contextPath(CONTEXTO))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
            .andExpect(jsonPath("$.path").value("/api/v1/tipos-servicio"))
            .andExpect(jsonPath("$.errores").isArray());
    }

    @Test
    void clienteContraAdminDevuelve403() throws Exception {
        mvc.perform(get(CONTEXTO + "/admin/usuarios").contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void preAuthorizeRechazaRolInsuficienteCon403() throws Exception {
        mvc.perform(post(CONTEXTO + "/tipos-servicio").contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void endpointSinImplementarDevuelve501() throws Exception {
        mvc.perform(get(CONTEXTO + "/tipos-servicio").contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isNotImplemented())
            .andExpect(jsonPath("$.codigo").value("NO_IMPLEMENTADO"));
    }

    @Test
    void gestorPasaElFiltroDeAdmin() throws Exception {
        mvc.perform(get(CONTEXTO + "/admin/usuarios").contextPath(CONTEXTO).with(como("GESTOR")))
            .andExpect(status().isNotImplemented());
    }

    @Test
    void loginEsPublico() throws Exception {
        mvc.perform(post(CONTEXTO + "/auth/login").contextPath(CONTEXTO))
            .andExpect(status().isNotImplemented());
    }
}
