package ar.edu.iessf.servife.identidad;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;
import ar.edu.iessf.servife.identidad.controller.DireccionController;
import ar.edu.iessf.servife.identidad.dto.DireccionResponse;
import ar.edu.iessf.servife.identidad.service.ServicioDeDirecciones;

/** A10 a A14: solo el cliente, el dueño sale del JWT, validación y códigos. Las reglas, en ServicioDeDireccionesTest. */
@WebMvcTest(controllers = DireccionController.class)
@Import({SeguridadConfig.class, JwtConfig.class, CorsConfig.class, EscritorDeErrores.class, UsuarioActual.class})
@TestPropertySource(properties = {
    "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres",
    "servife.jwt.duracion-access=15m",
    "servife.jwt.duracion-refresh=7d",
    "servife.cors.origenes-permitidos=http://localhost:8081"
})
class DireccionControllerTest {

    private static final String CONTEXTO = "/api/v1";
    private static final String RUTA = CONTEXTO + "/clientes/me/direcciones";

    @Autowired private MockMvc mvc;
    @MockitoBean private ServicioDeDirecciones servicio;

    private static JwtRequestPostProcessor como(String rol, UUID uuid) {
        return jwt().jwt(j -> j.subject(uuid.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void soloElCliente() throws Exception {
        mvc.perform(get(RUTA).contextPath(CONTEXTO)).andExpect(status().isUnauthorized());
        for (String rol : List.of("PRESTADOR", "GESTOR")) {
            mvc.perform(get(RUTA).contextPath(CONTEXTO).with(como(rol, UUID.randomUUID()))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void listaLasDelClienteDelToken() throws Exception {
        UUID ana = UUID.randomUUID();
        when(servicio.listar(ana)).thenReturn(List.of(new DireccionResponse(UUID.randomUUID(), "Casa", "San Martín 123", true)));

        mvc.perform(get(RUTA).contextPath(CONTEXTO).with(como("CLIENTE", ana)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].alias").value("Casa"))
            .andExpect(jsonPath("$[0].principal").value(true));
    }

    @Test
    void crearDevuelve201ConLocationYValidaLosCampos() throws Exception {
        UUID ana = UUID.randomUUID();
        UUID nueva = UUID.randomUUID();
        when(servicio.crear(eq(ana), any())).thenReturn(new DireccionResponse(nueva, "Casa", "San Martín 123", true));

        mvc.perform(post(RUTA).contextPath(CONTEXTO).with(como("CLIENTE", ana)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"alias\":\"Casa\",\"direccion\":\"San Martín 123\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/direcciones/" + nueva)))
            .andExpect(jsonPath("$.uuid").value(nueva.toString()));

        mvc.perform(post(RUTA).contextPath(CONTEXTO).with(como("CLIENTE", ana)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"alias\":\" \",\"direccion\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void borrarEs204YMarcarPrincipalDevuelveLaDireccion() throws Exception {
        UUID ana = UUID.randomUUID();
        UUID casa = UUID.randomUUID();
        when(servicio.marcarPrincipal(ana, casa)).thenReturn(new DireccionResponse(casa, "Casa", "San Martín 123", true));

        mvc.perform(delete(RUTA + "/" + casa).contextPath(CONTEXTO).with(como("CLIENTE", ana)))
            .andExpect(status().isNoContent());
        verify(servicio).eliminar(ana, casa);

        mvc.perform(patch(RUTA + "/" + casa + "/principal").contextPath(CONTEXTO).with(como("CLIENTE", ana)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.principal").value(true));
    }
}
