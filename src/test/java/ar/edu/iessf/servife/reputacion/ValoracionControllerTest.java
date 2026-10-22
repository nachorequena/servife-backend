package ar.edu.iessf.servife.reputacion;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;
import ar.edu.iessf.servife.reputacion.controller.ValoracionController;
import ar.edu.iessf.servife.reputacion.dto.ValoracionResponse;
import ar.edu.iessf.servife.reputacion.service.ServicioDeValoraciones;

/** D1 y D2 por HTTP: validación del cuerpo, roles y códigos. */
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

    // ── D1 ────────────────────────────────────────────────────────────────────

    private static String rutaValorar(UUID solicitud) {
        return CONTEXTO + "/solicitudes/" + solicitud + "/valoracion";
    }

    @Test
    void valorarDevuelve201ConLaResena() throws Exception {
        UUID solicitud = UUID.randomUUID();
        UUID resena = UUID.randomUUID();
        when(servicio.valorar(eq(solicitud), any())).thenReturn(
            new ValoracionResponse(resena, 5, "Impecable", "Ana Pérez", Instant.parse("2026-10-22T12:00:00Z")));

        mvc.perform(post(rutaValorar(solicitud)).contextPath(CONTEXTO).with(como("CLIENTE"))
                .contentType("application/json").content("{\"puntaje\":5,\"comentario\":\"Impecable\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.uuid").value(resena.toString()))
            .andExpect(jsonPath("$.puntaje").value(5))
            .andExpect(jsonPath("$.comentario").value("Impecable"))
            .andExpect(jsonPath("$.nombreCliente").value("Ana Pérez"));
    }

    @Test
    void valorarSinPuntajeEs400() throws Exception {
        mvc.perform(post(rutaValorar(UUID.randomUUID())).contextPath(CONTEXTO).with(como("CLIENTE"))
                .contentType("application/json").content("{\"comentario\":\"Bien\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"))
            .andExpect(jsonPath("$.errores[0].campo").value("puntaje"));
    }

    @Test
    void valorarConPuntajeFueraDeRangoEs400() throws Exception {
        for (int puntaje : new int[] {0, 6}) {
            mvc.perform(post(rutaValorar(UUID.randomUUID())).contextPath(CONTEXTO).with(como("CLIENTE"))
                    .contentType("application/json").content("{\"puntaje\":" + puntaje + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("puntaje"));
        }
    }

    @Test
    void valorarConComentarioDeMasDe500Es400() throws Exception {
        mvc.perform(post(rutaValorar(UUID.randomUUID())).contextPath(CONTEXTO).with(como("CLIENTE"))
                .contentType("application/json").content("{\"puntaje\":4,\"comentario\":\"" + "a".repeat(501) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("comentario"));
    }

    @Test
    void elPrestadorNoPuedeValorar() throws Exception {
        mvc.perform(post(rutaValorar(UUID.randomUUID())).contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType("application/json").content("{\"puntaje\":5}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void valorarUnaSolicitudNoFinalizadaEs409() throws Exception {
        UUID solicitud = UUID.randomUUID();
        when(servicio.valorar(eq(solicitud), any()))
            .thenThrow(new ConflictoException("TRANSICION_INVALIDA", "Solo se puede valorar una solicitud finalizada."));

        mvc.perform(post(rutaValorar(solicitud)).contextPath(CONTEXTO).with(como("CLIENTE"))
                .contentType("application/json").content("{\"puntaje\":5}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }
}
