package ar.edu.iessf.servife.catalogo;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.iessf.servife.catalogo.controller.TipoServicioController;
import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.catalogo.service.TipoServicioService;
import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;

/** B1 · GET /tipos-servicio es público: lo usa la pantalla de registro del prestador. */
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

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TipoServicioService servicio;

    @Test
    void listarEsPublicoYDevuelveLosTiposActivosSinElId() throws Exception {
        UUID uuid = UUID.fromString("7f1c2d4e-0000-4000-8000-0000000000aa");
        when(servicio.listarActivos())
            .thenReturn(List.of(new TipoServicioResponse(uuid, "Gas", "flame", true)));

        mvc.perform(get(CONTEXTO + "/tipos-servicio").contextPath(CONTEXTO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].uuid").value(uuid.toString()))
            .andExpect(jsonPath("$[0].nombre").value("Gas"))
            .andExpect(jsonPath("$[0].icono").value("flame"))
            .andExpect(jsonPath("$[0].requiereMatricula").value(true))
            .andExpect(jsonPath("$[0].id").doesNotExist());
    }
}
