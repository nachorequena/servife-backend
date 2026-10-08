package ar.edu.iessf.servife.catalogo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import ar.edu.iessf.servife.catalogo.controller.TipoServicioController;
import ar.edu.iessf.servife.catalogo.dto.TipoServicioRequest;
import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.catalogo.service.TipoServicioService;
import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.common.error.ErrorCampo;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;

/** B1 es público (registro del prestador); B2 a B4 son del gestor. */
@WebMvcTest(controllers = TipoServicioController.class)
@Import({SeguridadConfig.class, JwtConfig.class, CorsConfig.class, EscritorDeErrores.class})
@TestPropertySource(properties = {
    "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres",
    "servife.jwt.duracion-access=15m",
    "servife.jwt.duracion-refresh=7d",
    "servife.cors.origenes-permitidos=http://localhost:8081"
})
class TipoServicioControllerTest {

    private static final String CONTEXTO = "/api/v1";
    private static final String RUTA = CONTEXTO + "/tipos-servicio";
    private static final UUID UUID_TIPO = UUID.fromString("7f1c2d4e-0000-4000-8000-0000000000aa");
    private static final String CUERPO = "{\"nombre\":\"Pintura\",\"icono\":\"brush\",\"requiereMatricula\":false}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TipoServicioService servicio;

    private static JwtRequestPostProcessor como(String rol) {
        return jwt()
            .jwt(j -> j.subject("7f1c2d4e-0000-4000-8000-000000000001").claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void listarEsPublicoYDevuelveLosTiposActivosSinElId() throws Exception {
        when(servicio.listarActivos())
            .thenReturn(List.of(new TipoServicioResponse(UUID_TIPO, "Gas", "flame", true)));

        mvc.perform(get(RUTA).contextPath(CONTEXTO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].uuid").value(UUID_TIPO.toString()))
            .andExpect(jsonPath("$[0].nombre").value("Gas"))
            .andExpect(jsonPath("$[0].icono").value("flame"))
            .andExpect(jsonPath("$[0].requiereMatricula").value(true))
            .andExpect(jsonPath("$[0].id").doesNotExist());
    }

    @Test
    void crearDevuelve201ConLocationYElTipo() throws Exception {
        when(servicio.crear(new TipoServicioRequest("Pintura", "brush", false)))
            .thenReturn(new TipoServicioResponse(UUID_TIPO, "Pintura", "brush", false));

        mvc.perform(post(RUTA).contextPath(CONTEXTO).with(como("GESTOR"))
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/tipos-servicio/" + UUID_TIPO)))
            .andExpect(jsonPath("$.uuid").value(UUID_TIPO.toString()))
            .andExpect(jsonPath("$.nombre").value("Pintura"));
    }

    @Test
    void crearComoClienteEs403() throws Exception {
        mvc.perform(post(RUTA).contextPath(CONTEXTO).with(como("CLIENTE"))
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        verifyNoInteractions(servicio);
    }

    @Test
    void actualizarYEliminarComoClienteSon403() throws Exception {
        mvc.perform(put(RUTA + "/" + UUID_TIPO).contextPath(CONTEXTO).with(como("CLIENTE"))
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
            .andExpect(status().isForbidden());
        mvc.perform(delete(RUTA + "/" + UUID_TIPO).contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    void crearSinNombreEs400ConCampoNombre() throws Exception {
        mvc.perform(post(RUTA).contextPath(CONTEXTO).with(como("GESTOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"  \",\"icono\":\"brush\",\"requiereMatricula\":false}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"))
            .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
    }

    @Test
    void iconoInvalidoEs400ConCampoIcono() throws Exception {
        when(servicio.crear(any())).thenThrow(new ValidacionException("icono", "no es un ícono permitido"));

        mvc.perform(post(RUTA).contextPath(CONTEXTO).with(como("GESTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Pintura\",\"icono\":\"dragon\",\"requiereMatricula\":false}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"))
            .andExpect(jsonPath("$.errores[0].campo").value("icono"))
            .andExpect(jsonPath("$.errores[0].detalle").value("no es un ícono permitido"));
    }

    @Test
    void duplicadoEs409ConCampoNombre() throws Exception {
        when(servicio.crear(any())).thenThrow(new ConflictoException("TIPO_SERVICIO_DUPLICADO",
            "Ya existe un tipo de servicio con ese nombre.", new ErrorCampo("nombre", "ya existe")));

        mvc.perform(post(RUTA).contextPath(CONTEXTO).with(como("GESTOR"))
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("TIPO_SERVICIO_DUPLICADO"))
            .andExpect(jsonPath("$.errores[0].campo").value("nombre"))
            .andExpect(jsonPath("$.errores[0].detalle").value("ya existe"));
    }

    @Test
    void actualizarDevuelve200ConElTipo() throws Exception {
        when(servicio.actualizar(eq(UUID_TIPO), any()))
            .thenReturn(new TipoServicioResponse(UUID_TIPO, "Pintura", "brush", false));

        mvc.perform(put(RUTA + "/" + UUID_TIPO).contextPath(CONTEXTO).with(como("GESTOR"))
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").value("Pintura"));
    }

    @Test
    void eliminarDevuelve204() throws Exception {
        mvc.perform(delete(RUTA + "/" + UUID_TIPO).contextPath(CONTEXTO).with(como("GESTOR")))
            .andExpect(status().isNoContent());
        verify(servicio).eliminar(UUID_TIPO);
    }

    @Test
    void eliminarEnUsoEs409() throws Exception {
        doThrow(new ConflictoException("TIPO_SERVICIO_EN_USO", "Hay prestadores con este tipo de servicio."))
            .when(servicio).eliminar(UUID_TIPO);

        mvc.perform(delete(RUTA + "/" + UUID_TIPO).contextPath(CONTEXTO).with(como("GESTOR")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("TIPO_SERVICIO_EN_USO"));
    }
}
