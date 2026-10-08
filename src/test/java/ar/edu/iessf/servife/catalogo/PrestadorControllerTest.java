package ar.edu.iessf.servife.catalogo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

import ar.edu.iessf.servife.catalogo.controller.PrestadorController;
import ar.edu.iessf.servife.catalogo.dto.DisponibilidadResponse;
import ar.edu.iessf.servife.catalogo.dto.PerfilDeServicioResponse;
import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.catalogo.service.BuscadorDePrestadores;
import ar.edu.iessf.servife.catalogo.service.ServicioDePerfilDePrestador;
import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;

/** B7, B8 y GET /prestadores/me/perfil: roles y validación del cuerpo. La lógica se prueba en ServicioDePerfilDePrestadorTest. */
@WebMvcTest(controllers = PrestadorController.class)
@Import({SeguridadConfig.class, JwtConfig.class, CorsConfig.class, EscritorDeErrores.class})
@TestPropertySource(properties = {
    "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres",
    "servife.jwt.duracion-access=15m",
    "servife.jwt.duracion-refresh=7d",
    "servife.cors.origenes-permitidos=http://localhost:8081"
})
class PrestadorControllerTest {

    private static final String CONTEXTO = "/api/v1";
    private static final UUID YO = UUID.fromString("7f1c2d4e-0000-4000-8000-000000000001");
    private static final UUID TIPO = UUID.fromString("7f1c2d4e-0000-4000-8000-0000000000aa");

    @Autowired private MockMvc mvc;
    @MockitoBean private ServicioDePerfilDePrestador servicio;
    @MockitoBean private BuscadorDePrestadores buscador;
    @MockitoBean private UsuarioActual usuarioActual;

    private static JwtRequestPostProcessor como(String rol) {
        return jwt().jwt(j -> j.subject(YO.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private PerfilDeServicioResponse perfil() {
        return new PerfilDeServicioResponse(TIPO, new TipoServicioResponse(TIPO, "Gas", "flame", true), "Centro",
            new BigDecimal("-31.633300"), new BigDecimal("-60.700000"), 15, "Gasista", EstadoValidacion.PENDIENTE);
    }

    private void iniciarComoPrestador() {
        when(usuarioActual.uuid()).thenReturn(YO);
        when(usuarioActual.rol()).thenReturn(Rol.PRESTADOR);
    }

    @Test
    void obtenerMiPerfilDevuelveElPerfilDelPrestadorAutenticado() throws Exception {
        iniciarComoPrestador();
        when(servicio.obtenerPerfil(YO)).thenReturn(perfil());

        mvc.perform(get(CONTEXTO + "/prestadores/me/perfil").contextPath(CONTEXTO).with(como("PRESTADOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idTipoServicio").value(TIPO.toString()))
            .andExpect(jsonPath("$.tipoServicio.nombre").value("Gas"))
            .andExpect(jsonPath("$.estadoValidacion").value("PENDIENTE"))
            .andExpect(jsonPath("$.radioKm").value(15));
    }

    @Test
    void actualizarMiPerfilDevuelve200() throws Exception {
        iniciarComoPrestador();
        when(servicio.actualizarPerfil(eq(YO), any())).thenReturn(perfil());

        mvc.perform(put(CONTEXTO + "/prestadores/me/perfil").contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTipoServicio\":\"" + TIPO + "\",\"zona\":\"Centro\",\"radioKm\":15}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zona").value("Centro"));
    }

    @Test
    void perfilConRadioFueraDeRangoOSinTipoEs400() throws Exception {
        iniciarComoPrestador();
        mvc.perform(put(CONTEXTO + "/prestadores/me/perfil").contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTipoServicio\":\"" + TIPO + "\",\"radioKm\":101}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mvc.perform(put(CONTEXTO + "/prestadores/me/perfil").contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"zona\":\"Centro\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void reemplazarDisponibilidadDevuelveLosDias() throws Exception {
        iniciarComoPrestador();
        when(servicio.reemplazarDisponibilidad(eq(YO), any())).thenReturn(new DisponibilidadResponse(List.of(2)));

        mvc.perform(put(CONTEXTO + "/prestadores/me/disponibilidad").contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"dias\":[2]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dias[0]").value(2));
    }

    @Test
    void disponibilidadConDiaFueraDeRangoOMasDeSieteEs400() throws Exception {
        iniciarComoPrestador();
        mvc.perform(put(CONTEXTO + "/prestadores/me/disponibilidad").contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"dias\":[8]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put(CONTEXTO + "/prestadores/me/disponibilidad").contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"dias\":[1,2,3,4,5,6,7,1]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put(CONTEXTO + "/prestadores/me/disponibilidad").contextPath(CONTEXTO).with(como("PRESTADOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unClienteNoPuedeUsarLosEndpointsDelPrestador() throws Exception {
        mvc.perform(get(CONTEXTO + "/prestadores/me/perfil").contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isForbidden());
        mvc.perform(put(CONTEXTO + "/prestadores/me/perfil").contextPath(CONTEXTO).with(como("CLIENTE"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"idTipoServicio\":\"" + TIPO + "\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(put(CONTEXTO + "/prestadores/me/disponibilidad").contextPath(CONTEXTO).with(como("CLIENTE"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"dias\":[1]}"))
            .andExpect(status().isForbidden());
    }
}
