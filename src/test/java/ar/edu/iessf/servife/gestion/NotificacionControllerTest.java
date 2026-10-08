package ar.edu.iessf.servife.gestion;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;
import ar.edu.iessf.servife.gestion.controller.NotificacionController;
import ar.edu.iessf.servife.gestion.dto.AvisoResponse;
import ar.edu.iessf.servife.gestion.service.Avisos;

/** E11: lista, marcar leída y contador, siempre del usuario del token. */
@WebMvcTest(controllers = NotificacionController.class)
@Import({SeguridadConfig.class, JwtConfig.class, CorsConfig.class, EscritorDeErrores.class, UsuarioActual.class})
@TestPropertySource(properties = {
    "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres",
    "servife.jwt.duracion-access=15m",
    "servife.jwt.duracion-refresh=7d",
    "servife.cors.origenes-permitidos=http://localhost:8081"
})
class NotificacionControllerTest {

    private static final String CONTEXTO = "/api/v1";
    private static final String RUTA = CONTEXTO + "/notificaciones";
    private static final UUID YO = UUID.fromString("7f1c2d4e-0000-4000-8000-000000000001");
    private static final UUID AVISO = UUID.fromString("7f1c2d4e-0000-4000-8000-0000000000bb");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private Avisos avisos;

    private static JwtRequestPostProcessor como(String rol) {
        return jwt()
            .jwt(j -> j.subject(YO.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void sinTokenTodosLosEndpointsDan401() throws Exception {
        mvc.perform(get(RUTA).contextPath(CONTEXTO)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA + "/no-leidas").contextPath(CONTEXTO)).andExpect(status().isUnauthorized());
        mvc.perform(patch(RUTA + "/" + AVISO + "/leida").contextPath(CONTEXTO)).andExpect(status().isUnauthorized());
    }

    @Test
    void listarUsaElUsuarioDelTokenYLosValoresPorDefecto() throws Exception {
        AvisoResponse aviso = new AvisoResponse(AVISO, "SOLICITUD_NUEVA", "Nueva", "Cuerpo", null, false,
            Instant.parse("2026-10-01T12:00:00Z"));
        when(avisos.listar(YO, Rol.PRESTADOR, PageRequest.of(0, 20)))
            .thenReturn(new Pagina<>(List.of(aviso), 0, 20, 1, 1));

        mvc.perform(get(RUTA).contextPath(CONTEXTO).with(como("PRESTADOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido[0].uuid").value(AVISO.toString()))
            .andExpect(jsonPath("$.contenido[0].leida").value(false))
            .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void listarRechazaPaginasYTamaniosInvalidos() throws Exception {
        mvc.perform(get(RUTA + "?size=500").contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isBadRequest());
        mvc.perform(get(RUTA + "?size=0").contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isBadRequest());
        mvc.perform(get(RUTA + "?page=-1").contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listarAceptaElMaximoDe50() throws Exception {
        when(avisos.listar(YO, Rol.CLIENTE, PageRequest.of(2, 50)))
            .thenReturn(new Pagina<>(List.of(), 2, 50, 0, 0));

        mvc.perform(get(RUTA + "?page=2&size=50").contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tamanio").value(50));
    }

    @Test
    void marcarLeidaResponde204() throws Exception {
        mvc.perform(patch(RUTA + "/" + AVISO + "/leida").contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isNoContent());

        verify(avisos).marcarLeido(YO, Rol.CLIENTE, AVISO);
    }

    @Test
    void marcarLeidaUnAvisoAjenoResponde404() throws Exception {
        doThrow(new RecursoNoEncontradoException("No existe.")).when(avisos).marcarLeido(YO, Rol.CLIENTE, AVISO);

        mvc.perform(patch(RUTA + "/" + AVISO + "/leida").contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isNotFound());
    }

    @Test
    void contadorDevuelveSoloLaCantidad() throws Exception {
        when(avisos.contarNoLeidos(YO, Rol.GESTOR)).thenReturn(3L);

        mvc.perform(get(RUTA + "/no-leidas").contextPath(CONTEXTO).with(como("GESTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cantidad").value(3));
    }
}
