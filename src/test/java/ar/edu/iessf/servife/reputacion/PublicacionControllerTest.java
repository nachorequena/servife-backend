package ar.edu.iessf.servife.reputacion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

import java.time.Instant;
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
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;
import ar.edu.iessf.servife.reputacion.controller.PublicacionController;
import ar.edu.iessf.servife.reputacion.dto.PublicacionRequest;
import ar.edu.iessf.servife.reputacion.dto.PublicacionResponse;
import ar.edu.iessf.servife.reputacion.service.ServicioDePublicaciones;

/** D3, D4, D5, D6 y D8 por HTTP: roles, validación del cuerpo, códigos y Location. La lógica la cubre PublicacionesHttpTest. */
@WebMvcTest(controllers = PublicacionController.class)
@Import({SeguridadConfig.class, JwtConfig.class, CorsConfig.class, EscritorDeErrores.class})
@TestPropertySource(properties = {
    "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres",
    "servife.jwt.duracion-access=15m",
    "servife.jwt.duracion-refresh=7d",
    "servife.cors.origenes-permitidos=http://localhost:8081"
})
class PublicacionControllerTest {

    private static final String CONTEXTO = "/api/v1";
    private static final UUID YO = UUID.fromString("7f1c2d4e-0000-4000-8000-000000000001");
    private static final UUID PRESTADOR = UUID.fromString("7f1c2d4e-0000-4000-8000-0000000000aa");
    private static final UUID PUBLICACION = UUID.fromString("7f1c2d4e-0000-4000-8000-0000000000bb");
    private static final String CUERPO = "{\"titulo\":\"Baño nuevo\",\"descripcion\":\"Cambio de cañerías\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDePublicaciones servicio;

    private static JwtRequestPostProcessor como(String rol) {
        return jwt()
            .jwt(j -> j.subject(YO.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private static PublicacionResponse publicacion() {
        return new PublicacionResponse(PUBLICACION, "Baño nuevo", "Cambio de cañerías", LocalDate.of(2026, 10, 1),
            List.of(), 5, null, Instant.parse("2026-10-02T12:00:00Z"));
    }

    @Test
    void sinTokenTodosDan401() throws Exception {
        mvc.perform(get(CONTEXTO + "/prestadores/" + PRESTADOR + "/publicaciones").contextPath(CONTEXTO))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(CONTEXTO + "/prestadores/me/publicaciones").contextPath(CONTEXTO))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(CONTEXTO + "/publicaciones").contextPath(CONTEXTO)
                .contentType("application/json").content(CUERPO))
            .andExpect(status().isUnauthorized());
        mvc.perform(delete(CONTEXTO + "/publicaciones/" + PUBLICACION).contextPath(CONTEXTO))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void d3LoVeCualquierAutenticadoConPaginaPorDefecto() throws Exception {
        when(servicio.listarDePrestador(PRESTADOR, null, null))
            .thenReturn(new Pagina<>(List.of(publicacion()), 0, 20, 1, 1));

        mvc.perform(get(CONTEXTO + "/prestadores/" + PRESTADOR + "/publicaciones").contextPath(CONTEXTO)
                .with(como("CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido[0].uuid").value(PUBLICACION.toString()))
            .andExpect(jsonPath("$.contenido[0].puntaje").value(5))
            .andExpect(jsonPath("$.contenido[0].fechaTrabajo").value("2026-10-01"))
            .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void d3DeUnPrestadorNoVisibleEs404() throws Exception {
        when(servicio.listarDePrestador(PRESTADOR, null, null)).thenThrow(new RecursoNoEncontradoException("No existe."));

        mvc.perform(get(CONTEXTO + "/prestadores/" + PRESTADOR + "/publicaciones").contextPath(CONTEXTO)
                .with(como("CLIENTE")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void d8NoEsDeLaRutaConUuidYPasaLaPagina() throws Exception {
        when(servicio.listarMias(1, 5)).thenReturn(new Pagina<>(List.of(), 1, 5, 0, 0));

        mvc.perform(get(CONTEXTO + "/prestadores/me/publicaciones?page=1&size=5").contextPath(CONTEXTO)
                .with(como("PRESTADOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagina").value(1))
            .andExpect(jsonPath("$.tamanio").value(5));
        verify(servicio, never()).listarDePrestador(any(), any(), any());
    }

    @Test
    void soloElPrestadorGestionaSusPublicaciones() throws Exception {
        mvc.perform(get(CONTEXTO + "/prestadores/me/publicaciones").contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isForbidden());
        mvc.perform(post(CONTEXTO + "/publicaciones").contextPath(CONTEXTO).with(como("CLIENTE"))
                .contentType("application/json").content(CUERPO))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        mvc.perform(put(CONTEXTO + "/publicaciones/" + PUBLICACION).contextPath(CONTEXTO).with(como("GESTOR"))
                .contentType("application/json").content(CUERPO))
            .andExpect(status().isForbidden());
        mvc.perform(delete(CONTEXTO + "/publicaciones/" + PUBLICACION).contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    void crearResponde201ConLocation() throws Exception {
        when(servicio.crear(any(PublicacionRequest.class))).thenReturn(publicacion());

        mvc.perform(post(CONTEXTO + "/publicaciones").contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType("application/json").content(CUERPO))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost/api/v1/publicaciones/" + PUBLICACION))
            .andExpect(jsonPath("$.titulo").value("Baño nuevo"));
    }

    @Test
    void crearSinTituloOConTituloLargoEs400EnElCampo() throws Exception {
        mvc.perform(post(CONTEXTO + "/publicaciones").contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType("application/json").content("{\"titulo\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"))
            .andExpect(jsonPath("$.errores[0].campo").value("titulo"))
            .andExpect(jsonPath("$.errores[0].detalle").value("es obligatorio"));
        mvc.perform(post(CONTEXTO + "/publicaciones").contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType("application/json").content("{\"titulo\":\"" + "x".repeat(121) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("titulo"));
        verifyNoInteractions(servicio);
    }

    @Test
    void crearConMasDeCincoImagenesEs400() throws Exception {
        String seis = String.join(",", java.util.Collections.nCopies(6, "\"" + UUID.randomUUID() + "\""));
        mvc.perform(post(CONTEXTO + "/publicaciones").contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType("application/json").content("{\"titulo\":\"T\",\"imagenIds\":[" + seis + "]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("imagenIds"));
    }

    @Test
    void unErrorDeNegocioDelServicioSaleEnElFormatoUnico() throws Exception {
        when(servicio.crear(any(PublicacionRequest.class)))
            .thenThrow(new ValidacionException("uuidSolicitud", "no es un trabajo terminado tuyo"));

        mvc.perform(post(CONTEXTO + "/publicaciones").contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType("application/json").content(CUERPO))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("uuidSolicitud"))
            .andExpect(jsonPath("$.errores[0].detalle").value("no es un trabajo terminado tuyo"));
    }

    @Test
    void actualizarResponde200() throws Exception {
        when(servicio.actualizar(eq(PUBLICACION), any(PublicacionRequest.class))).thenReturn(publicacion());

        mvc.perform(put(CONTEXTO + "/publicaciones/" + PUBLICACION).contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType("application/json").content(CUERPO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uuid").value(PUBLICACION.toString()));
    }

    @Test
    void actualizarUnaAjenaEs404() throws Exception {
        when(servicio.actualizar(eq(PUBLICACION), any(PublicacionRequest.class)))
            .thenThrow(new RecursoNoEncontradoException("No existe."));

        mvc.perform(put(CONTEXTO + "/publicaciones/" + PUBLICACION).contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType("application/json").content(CUERPO))
            .andExpect(status().isNotFound());
    }

    @Test
    void eliminarResponde204YUnaAjenaEs404() throws Exception {
        mvc.perform(delete(CONTEXTO + "/publicaciones/" + PUBLICACION).contextPath(CONTEXTO).with(como("PRESTADOR")))
            .andExpect(status().isNoContent());
        verify(servicio).eliminar(PUBLICACION);

        UUID ajena = UUID.randomUUID();
        doThrow(new RecursoNoEncontradoException("No existe.")).when(servicio).eliminar(ajena);
        mvc.perform(delete(CONTEXTO + "/publicaciones/" + ajena).contextPath(CONTEXTO).with(como("PRESTADOR")))
            .andExpect(status().isNotFound());
    }

    @Test
    void uuidMalFormadoEs400() throws Exception {
        mvc.perform(delete(CONTEXTO + "/publicaciones/no-es-un-uuid").contextPath(CONTEXTO).with(como("PRESTADOR")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }
}
