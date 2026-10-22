package ar.edu.iessf.servife.reputacion;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;
import ar.edu.iessf.servife.reputacion.controller.ValoracionController;
import ar.edu.iessf.servife.reputacion.dto.ValoracionResponse;
import ar.edu.iessf.servife.reputacion.service.ServicioDeValoraciones;

/** D2 por HTTP. D1 sigue en 501 hasta que exista FINALIZADA → VALORADA. */
@WebMvcTest(controllers = ValoracionController.class)
@Import({SeguridadConfig.class, JwtConfig.class, CorsConfig.class, EscritorDeErrores.class})
@TestPropertySource(properties = {
    "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres",
    "servife.jwt.duracion-access=15m",
    "servife.jwt.duracion-refresh=7d",
    "servife.cors.origenes-permitidos=http://localhost:8081"
})
class ValoracionControllerTest {

    private static final String CONTEXTO = "/api/v1";
    private static final UUID YO = UUID.fromString("7f1c2d4e-0000-4000-8000-000000000001");
    private static final UUID PRESTADOR = UUID.fromString("7f1c2d4e-0000-4000-8000-0000000000aa");
    private static final String RUTA = CONTEXTO + "/prestadores/" + PRESTADOR + "/valoraciones";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDeValoraciones servicio;

    private static JwtRequestPostProcessor como(String rol) {
        return jwt()
            .jwt(j -> j.subject(YO.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void sinTokenEs401() throws Exception {
        mvc.perform(get(RUTA).contextPath(CONTEXTO)).andExpect(status().isUnauthorized());
    }

    @Test
    void listaLasResenasPaginadas() throws Exception {
        UUID resena = UUID.randomUUID();
        when(servicio.listarDePrestador(PRESTADOR, 0, 10)).thenReturn(new Pagina<>(List.of(
            new ValoracionResponse(resena, 4, "Muy prolijo", "Ana Pérez", Instant.parse("2026-10-02T12:00:00Z"))),
            0, 10, 1, 1));

        mvc.perform(get(RUTA + "?page=0&size=10").contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido[0].uuid").value(resena.toString()))
            .andExpect(jsonPath("$.contenido[0].puntaje").value(4))
            .andExpect(jsonPath("$.contenido[0].comentario").value("Muy prolijo"))
            .andExpect(jsonPath("$.contenido[0].nombreCliente").value("Ana Pérez"))
            .andExpect(jsonPath("$.tamanio").value(10));
    }

    @Test
    void deUnPrestadorNoVisibleEs404() throws Exception {
        when(servicio.listarDePrestador(PRESTADOR, null, null)).thenThrow(new RecursoNoEncontradoException("No existe."));

        mvc.perform(get(RUTA).contextPath(CONTEXTO).with(como("PRESTADOR")))
            .andExpect(status().isNotFound());
    }

    @Test
    void valorarTodaviaNoEstaImplementado() throws Exception {
        mvc.perform(post(CONTEXTO + "/solicitudes/" + UUID.randomUUID() + "/valoracion").contextPath(CONTEXTO)
                .with(como("CLIENTE")))
            .andExpect(status().isNotImplemented())
            .andExpect(jsonPath("$.codigo").value("NO_IMPLEMENTADO"));
    }
}
