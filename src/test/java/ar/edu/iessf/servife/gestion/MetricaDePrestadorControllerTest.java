package ar.edu.iessf.servife.gestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;
import ar.edu.iessf.servife.gestion.controller.MetricaDePrestadorController;
import ar.edu.iessf.servife.gestion.dto.ConteoPorMes;
import ar.edu.iessf.servife.gestion.dto.ConteoPorNombre;
import ar.edu.iessf.servife.gestion.dto.ConteoPorServicio;
import ar.edu.iessf.servife.gestion.dto.MetricasDePrestadorResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeMetricasDePrestador;

/** E16: roles, el uuid sale del JWT y formato. Los conteos se prueban en ServicioDeMetricasDePrestadorTest. */
@WebMvcTest(controllers = MetricaDePrestadorController.class)
@Import({SeguridadConfig.class, JwtConfig.class, CorsConfig.class, EscritorDeErrores.class, UsuarioActual.class})
@TestPropertySource(properties = {
    "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres",
    "servife.jwt.duracion-access=15m",
    "servife.jwt.duracion-refresh=7d",
    "servife.cors.origenes-permitidos=http://localhost:8081"
})
class MetricaDePrestadorControllerTest {

    private static final String CONTEXTO = "/api/v1";
    private static final String RUTA = CONTEXTO + "/prestadores/me/metricas";

    @Autowired private MockMvc mvc;
    @MockitoBean private ServicioDeMetricasDePrestador servicio;

    private static JwtRequestPostProcessor como(String rol, UUID uuid) {
        return jwt().jwt(j -> j.subject(uuid.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void sinTokenEs401YOtrosRoles403() throws Exception {
        mvc.perform(get(RUTA).contextPath(CONTEXTO)).andExpect(status().isUnauthorized());
        for (String rol : List.of("CLIENTE", "GESTOR")) {
            mvc.perform(get(RUTA).contextPath(CONTEXTO).with(como(rol, UUID.randomUUID()))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void prestadorRecibeSusMetricas() throws Exception {
        UUID yo = UUID.randomUUID();
        LocalDate desde = LocalDate.of(2026, 4, 1);
        when(servicio.obtener(eq(yo), eq(desde), any())).thenReturn(new MetricasDePrestadorResponse(desde,
            LocalDate.of(2026, 5, 31), 3, List.of(new ConteoPorNombre("PENDIENTE", 1)), List.of(new ConteoPorNombre("Ana", 2)),
            List.of(new ConteoPorServicio("Gas", 3, 1)), List.of(new ConteoPorMes("2026-04", 3)), new BigDecimal("4.5")));

        mvc.perform(get(RUTA).contextPath(CONTEXTO).param("desde", "2026-04-01").with(como("PRESTADOR", yo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSolicitudes").value(3))
            .andExpect(jsonPath("$.porCliente[0].nombre").value("Ana"))
            .andExpect(jsonPath("$.porServicio[0].realizados").value(1))
            .andExpect(jsonPath("$.valoracionPromedio").value(4.5));
    }
}
