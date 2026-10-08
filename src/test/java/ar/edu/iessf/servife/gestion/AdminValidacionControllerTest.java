package ar.edu.iessf.servife.gestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;
import ar.edu.iessf.servife.gestion.controller.AdminValidacionController;
import ar.edu.iessf.servife.gestion.dto.Decision;
import ar.edu.iessf.servife.gestion.dto.PrestadorPendienteResponse;
import ar.edu.iessf.servife.gestion.dto.ValidacionResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeValidacion;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;

/** E5 y E6: roles y validación del cuerpo. La lógica se prueba en ServicioDeValidacionTest. */
@WebMvcTest(controllers = AdminValidacionController.class)
@Import({SeguridadConfig.class, JwtConfig.class, CorsConfig.class, EscritorDeErrores.class})
@TestPropertySource(properties = {
    "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres",
    "servife.jwt.duracion-access=15m",
    "servife.jwt.duracion-refresh=7d",
    "servife.cors.origenes-permitidos=http://localhost:8081"
})
class AdminValidacionControllerTest {

    private static final String CONTEXTO = "/api/v1";
    private static final UUID PRESTADOR = UUID.fromString("7f1c2d4e-0000-4000-8000-0000000000bb");

    @Autowired private MockMvc mvc;
    @MockitoBean private ServicioDeValidacion servicio;

    private static JwtRequestPostProcessor como(String rol) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private static String ruta() {
        return CONTEXTO + "/admin/prestadores/" + PRESTADOR + "/validacion";
    }

    @Test
    void clienteYPrestadorNoPuedenListarNiDecidir() throws Exception {
        for (String rol : List.of("CLIENTE", "PRESTADOR")) {
            mvc.perform(get(CONTEXTO + "/admin/validaciones").contextPath(CONTEXTO).with(como(rol)))
                .andExpect(status().isForbidden());
            mvc.perform(patch(ruta()).contextPath(CONTEXTO).with(como(rol)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"APROBAR\"}"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void gestorListaLosPendientes() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(servicio.listarPendientes(any())).thenReturn(new Pagina<>(List.of(new PrestadorPendienteResponse(uuid,
            "Pedro", "p@mail.com", new TipoServicioResponse(uuid, "Gas", "flame", true),
            Instant.parse("2026-10-01T10:00:00Z"))), 0, 20, 1, 1));

        mvc.perform(get(CONTEXTO + "/admin/validaciones").contextPath(CONTEXTO).with(como("GESTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido[0].uuid").value(uuid.toString()))
            .andExpect(jsonPath("$.contenido[0].nombreApellido").value("Pedro"))
            .andExpect(jsonPath("$.contenido[0].tipoServicio.nombre").value("Gas"))
            .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void gestorDecideYRecibeElEstado() throws Exception {
        when(servicio.decidir(eq(PRESTADOR), eq(Decision.RECHAZAR), eq("Falta matrícula")))
            .thenReturn(new ValidacionResponse(PRESTADOR, EstadoValidacion.RECHAZADO));

        mvc.perform(patch(ruta()).contextPath(CONTEXTO).with(como("GESTOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"RECHAZAR\",\"motivo\":\"Falta matrícula\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uuid").value(PRESTADOR.toString()))
            .andExpect(jsonPath("$.estadoValidacion").value("RECHAZADO"));
        verify(servicio).decidir(PRESTADOR, Decision.RECHAZAR, "Falta matrícula");
    }

    @Test
    void decisionInvalidaFaltanteOMotivoLargoEs400() throws Exception {
        for (String cuerpo : List.of("{\"decision\":\"TAL_VEZ\"}", "{}",
                "{\"decision\":\"RECHAZAR\",\"motivo\":\"" + "x".repeat(256) + "\"}")) {
            mvc.perform(patch(ruta()).contextPath(CONTEXTO).with(como("GESTOR")).contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpo))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(servicio);
    }
}
