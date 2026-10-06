package ar.edu.iessf.servife.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;
import ar.edu.iessf.servife.identidad.controller.AuthController;
import ar.edu.iessf.servife.identidad.controller.UsuarioController;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.dto.ActualizarUsuarioRequest;
import ar.edu.iessf.servife.identidad.dto.CambiarContraseniaRequest;
import ar.edu.iessf.servife.identidad.dto.UsuarioResponse;
import ar.edu.iessf.servife.identidad.service.ServicioDeCuenta;
import ar.edu.iessf.servife.identidad.service.ServicioDeRegistro;
import ar.edu.iessf.servife.identidad.service.ServicioDeSesion;

/** A4 (GET /auth/me), A6 (PUT /usuarios/me) y A7 (PATCH /usuarios/me/password). */
@WebMvcTest(controllers = {UsuarioController.class, AuthController.class})
@Import({SeguridadConfig.class, JwtConfig.class, CorsConfig.class, EscritorDeErrores.class, UsuarioActual.class})
@TestPropertySource(properties = {
    "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres",
    "servife.jwt.duracion-access=15m",
    "servife.jwt.duracion-refresh=7d",
    "servife.cors.origenes-permitidos=http://localhost:8081"
})
class UsuarioControllerTest {

    private static final String CONTEXTO = "/api/v1";
    private static final UUID UUID_USUARIO = UUID.fromString("7f1c2d4e-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDeCuenta cuenta;

    // /auth/me vive en AuthController, que necesita estos dos para armarse.
    @MockitoBean
    private ServicioDeRegistro registro;

    @MockitoBean
    private ServicioDeSesion sesion;

    private static JwtRequestPostProcessor como(String rol) {
        return jwt()
            .jwt(j -> j.subject(UUID_USUARIO.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private ResultActions actualizar(String json) throws Exception {
        return mvc.perform(put(CONTEXTO + "/usuarios/me").contextPath(CONTEXTO).with(como("CLIENTE"))
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions cambiarContrasenia(String json) throws Exception {
        return mvc.perform(patch(CONTEXTO + "/usuarios/me/password").contextPath(CONTEXTO).with(como("CLIENTE"))
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static UsuarioResponse cliente() {
        return new UsuarioResponse(UUID_USUARIO, Rol.CLIENTE, "Ana", "ana@mail.com", "123", "Calle 1",
            LocalDate.of(1990, 5, 17), null);
    }

    @Test
    void meSinTokenDa401() throws Exception {
        mvc.perform(get(CONTEXTO + "/auth/me").contextPath(CONTEXTO))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void meDeClienteNoIncluyeEstadoDeValidacion() throws Exception {
        when(cuenta.obtener(UUID_USUARIO, Rol.CLIENTE)).thenReturn(cliente());

        mvc.perform(get(CONTEXTO + "/auth/me").contextPath(CONTEXTO).with(como("CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uuid").value(UUID_USUARIO.toString()))
            .andExpect(jsonPath("$.rol").value("CLIENTE"))
            .andExpect(jsonPath("$.estadoValidacion").doesNotExist())
            .andExpect(jsonPath("$.contrasenia").doesNotExist());
    }

    @Test
    void meDePrestadorIncluyeEstadoDeValidacion() throws Exception {
        when(cuenta.obtener(UUID_USUARIO, Rol.PRESTADOR)).thenReturn(new UsuarioResponse(UUID_USUARIO,
            Rol.PRESTADOR, "Pedro", "pedro@mail.com", null, null, null, EstadoValidacion.PENDIENTE));

        mvc.perform(get(CONTEXTO + "/auth/me").contextPath(CONTEXTO).with(como("PRESTADOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estadoValidacion").value("PENDIENTE"));
    }

    @Test
    void actualizarDevuelveLosDatosGuardados() throws Exception {
        when(cuenta.actualizar(eq(UUID_USUARIO), eq(Rol.CLIENTE), any())).thenReturn(cliente());

        actualizar("""
            {"nombreApellido":"Ana","telefono":"123","direccion":"Calle 1","fecNacimiento":"1990-05-17"}""")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombreApellido").value("Ana"))
            .andExpect(jsonPath("$.telefono").value("123"))
            .andExpect(jsonPath("$.fecNacimiento").value("1990-05-17"));

        ArgumentCaptor<ActualizarUsuarioRequest> pedido = ArgumentCaptor.forClass(ActualizarUsuarioRequest.class);
        verify(cuenta).actualizar(eq(UUID_USUARIO), eq(Rol.CLIENTE), pedido.capture());
        assertThat(pedido.getValue().direccion()).isEqualTo("Calle 1");
    }

    @Test
    void actualizarConNombreEnBlancoOFechaFuturaDa400() throws Exception {
        actualizar("{\"nombreApellido\":\"  \"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"))
            .andExpect(jsonPath("$.errores[0].campo").value("nombreApellido"));

        actualizar("{\"nombreApellido\":\"Ana\",\"fecNacimiento\":\"2999-01-01\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("fecNacimiento"));
    }

    @Test
    void actualizarConValidacionDelServicioDa400EnElCampoTelefono() throws Exception {
        when(cuenta.actualizar(eq(UUID_USUARIO), eq(Rol.CLIENTE), any()))
            .thenThrow(new ValidacionException("telefono", "los gestores no tienen teléfono"));

        actualizar("{\"nombreApellido\":\"Gina\",\"telefono\":\"123\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"))
            .andExpect(jsonPath("$.errores[0].campo").value("telefono"));
    }

    @Test
    void cambiarContraseniaOkDa204YLlamaAlServicio() throws Exception {
        cambiarContrasenia("{\"contraseniaActual\":\"clave1234\",\"contraseniaNueva\":\"nueva-clave9\"}")
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(cuenta).cambiarContrasenia(eq(UUID_USUARIO), eq(Rol.CLIENTE),
            eq(new CambiarContraseniaRequest("clave1234", "nueva-clave9")));
    }

    @Test
    void cambiarContraseniaConActualMalaDa400EnContraseniaActual() throws Exception {
        doThrow(new ValidacionException("contraseniaActual", "no coincide"))
            .when(cuenta).cambiarContrasenia(eq(UUID_USUARIO), eq(Rol.CLIENTE), any());

        cambiarContrasenia("{\"contraseniaActual\":\"mala\",\"contraseniaNueva\":\"nueva-clave9\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("contraseniaActual"));
    }

    @Test
    void cambiarContraseniaNuevaDebilOMuyLargaDa400() throws Exception {
        cambiarContrasenia("{\"contraseniaActual\":\"clave1234\",\"contraseniaNueva\":\"corta1\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("contraseniaNueva"));

        // 70 caracteres pero 138 bytes: la regex la acepta y la rechaza el controller antes de llegar al servicio.
        String larga = "a1" + "ñ".repeat(68);
        cambiarContrasenia("{\"contraseniaActual\":\"clave1234\",\"contraseniaNueva\":\"" + larga + "\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("contraseniaNueva"));
    }

    @Test
    void usuariosMeSinTokenDa401() throws Exception {
        mvc.perform(put(CONTEXTO + "/usuarios/me").contextPath(CONTEXTO)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
