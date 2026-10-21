package ar.edu.iessf.servife.gestion;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;
import ar.edu.iessf.servife.gestion.controller.MetricaController;
import ar.edu.iessf.servife.gestion.dto.MetricasResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeMetricas;

/** E9: roles y formato. Los conteos se prueban en ServicioDeMetricasTest. */
@WebMvcTest(controllers = MetricaController.class)
@Import({SeguridadConfig.class, JwtConfig.class, CorsConfig.class, EscritorDeErrores.class})
@TestPropertySource(properties = {
    "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres",
    "servife.jwt.duracion-access=15m",
    "servife.jwt.duracion-refresh=7d",
    "servife.cors.origenes-permitidos=http://localhost:8081"
})
class MetricaControllerTest {

    private static final String CONTEXTO = "/api/v1";
    private static final String RUTA = CONTEXTO + "/admin/metricas";

    @Autowired private MockMvc mvc;
    @MockitoBean private ServicioDeMetricas servicio;

    private static JwtRequestPostProcessor como(String rol) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void sinTokenEs401() throws Exception {
        mvc.perform(get(RUTA).contextPath(CONTEXTO)).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    void clienteYPrestadorReciben403() throws Exception {
        for (String rol : List.of("CLIENTE", "PRESTADOR")) {
            mvc.perform(get(RUTA).contextPath(CONTEXTO).with(como(rol))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void gestorRecibeLosTresContadores() throws Exception {
        when(servicio.obtener()).thenReturn(new MetricasResponse(3, 2, 5));

        mvc.perform(get(RUTA).contextPath(CONTEXTO).with(como("GESTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviciosActivos").value(3))
            .andExpect(jsonPath("$.solicitudesPendientes").value(2))
            .andExpect(jsonPath("$.validacionesPendientes").value(5));
    }
}
