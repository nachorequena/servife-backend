package ar.edu.iessf.servife.identidad;

import static org.hamcrest.Matchers.hasItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.ErrorCampo;
import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;
import ar.edu.iessf.servife.identidad.controller.AuthController;
import ar.edu.iessf.servife.identidad.dto.ConfirmarRecuperacionRequest;
import ar.edu.iessf.servife.identidad.dto.LoginRequest;
import ar.edu.iessf.servife.identidad.dto.RecuperarRequest;
import ar.edu.iessf.servife.identidad.dto.RefreshRequest;
import ar.edu.iessf.servife.identidad.dto.RegistroRequest;
import ar.edu.iessf.servife.identidad.dto.TokensResponse;
import ar.edu.iessf.servife.identidad.dto.UsuarioResponse;
import ar.edu.iessf.servife.identidad.service.ServicioDeCuenta;
import ar.edu.iessf.servife.identidad.service.ServicioDeRecuperacion;
import ar.edu.iessf.servife.identidad.service.ServicioDeRegistro;
import ar.edu.iessf.servife.identidad.service.ServicioDeSesion;

/** Endpoints de /auth (A1 a A4, A8 y A9). */
@WebMvcTest(controllers = AuthController.class)
@Import({SeguridadConfig.class, JwtConfig.class, CorsConfig.class, EscritorDeErrores.class})
@TestPropertySource(properties = {
    "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres",
    "servife.jwt.duracion-access=15m",
    "servife.jwt.duracion-refresh=7d",
    "servife.cors.origenes-permitidos=http://localhost:8081"
})
class AuthControllerTest {

    private static final String CONTEXTO = "/api/v1";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDeRegistro registro;

    @MockitoBean
    private ServicioDeSesion sesion;

    @MockitoBean
    private ServicioDeCuenta cuenta;

    @MockitoBean
    private UsuarioActual usuarioActual;

    @MockitoBean
    private ServicioDeRecuperacion recuperacion;

    private ResultActions registrar(String json) throws Exception {
        return mvc.perform(post(CONTEXTO + "/auth/registro").contextPath(CONTEXTO)
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String cuerpo(String rol, String contrasenia) {
        return """
            {"rol":"%s","nombreApellido":"Ana Pérez","email":"ana@mail.com","contrasenia":"%s"}
            """.formatted(rol, contrasenia);
    }

    @Test
    void registroFelizDevuelve201SinContraseniaNiLocation() throws Exception {
        UUID uuid = UUID.fromString("7f1c2d4e-0000-4000-8000-0000000000bb");
        when(registro.registrar(any(RegistroRequest.class))).thenReturn(
            new UsuarioResponse(uuid, Rol.CLIENTE, "Ana Pérez", "ana@mail.com", null, null, null, null));

        registrar(cuerpo("CLIENTE", "clave1234"))
            .andExpect(status().isCreated())
            .andExpect(header().doesNotExist("Location"))
            .andExpect(jsonPath("$.uuid").value(uuid.toString()))
            .andExpect(jsonPath("$.rol").value("CLIENTE"))
            .andExpect(jsonPath("$.email").value("ana@mail.com"))
            .andExpect(jsonPath("$.contrasenia").doesNotExist())
            .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void emailConEspaciosSeRecortaAntesDeValidarYLlegaRecortadoAlServicio() throws Exception {
        when(registro.registrar(any(RegistroRequest.class))).thenReturn(
            new UsuarioResponse(UUID.randomUUID(), Rol.CLIENTE, "Ana Pérez", "ana@mail.com", null, null, null, null));

        registrar("""
            {"rol":"CLIENTE","nombreApellido":"Ana Pérez","email":" Ana@Mail.com ","contrasenia":"clave1234"}
            """)
            .andExpect(status().isCreated());

        ArgumentCaptor<RegistroRequest> recibido = ArgumentCaptor.forClass(RegistroRequest.class);
        verify(registro).registrar(recibido.capture());
        assertThat(recibido.getValue().email()).isEqualTo("Ana@Mail.com");
    }

    @Test
    void rolGestorDa400ConErrorEnRol() throws Exception {
        registrar(cuerpo("GESTOR", "clave1234"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"))
            .andExpect(jsonPath("$.errores[0].campo").value("rol"));
        verify(registro, never()).registrar(any());
    }

    @Test
    void contraseniaSinNumeroDa400ConErrorEnContrasenia() throws Exception {
        registrar(cuerpo("CLIENTE", "abcdefgh"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"))
            .andExpect(jsonPath("$.errores[0].campo").value("contrasenia"))
            .andExpect(jsonPath("$.errores[0].detalle")
                .value("mínimo 8 caracteres, con al menos una letra y un número"));
    }

    @Test
    void emailInvalidoDa400() throws Exception {
        registrar("""
            {"rol":"CLIENTE","nombreApellido":"Ana","email":"no-es-un-mail","contrasenia":"clave1234"}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("email"));
    }

    @Test
    void emailYaRegistradoDa409() throws Exception {
        when(registro.registrar(any(RegistroRequest.class)))
            .thenThrow(new ConflictoException("EMAIL_YA_REGISTRADO", "Ese correo ya tiene una cuenta.",
                new ErrorCampo("email", "ya existe")));

        registrar(cuerpo("CLIENTE", "clave1234"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("EMAIL_YA_REGISTRADO"))
            .andExpect(jsonPath("$.errores[0].campo").value("email"))
            .andExpect(jsonPath("$.errores[0].detalle").value("ya existe"));
    }

    @Test
    void validacionDelServicioDa400ConElCampo() throws Exception {
        when(registro.registrar(any(RegistroRequest.class)))
            .thenThrow(new ValidacionException("idTipoServicio", "es obligatorio para prestadores"));

        registrar(cuerpo("PRESTADOR", "clave1234"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"))
            .andExpect(jsonPath("$.mensaje").value("Hay campos con errores."))
            .andExpect(jsonPath("$.errores[0].campo").value("idTipoServicio"))
            .andExpect(jsonPath("$.errores[0].detalle").value("es obligatorio para prestadores"));
    }

    private ResultActions enviar(String ruta, String json) throws Exception {
        return mvc.perform(post(CONTEXTO + ruta).contextPath(CONTEXTO)
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // --- A2 login ---

    @Test
    void loginConCuerpoValidoDa200ConTokensYRolSinAutenticacion() throws Exception {
        when(sesion.iniciar(any(LoginRequest.class)))
            .thenReturn(new TokensResponse("el-access", "el-refresh", Rol.PRESTADOR));

        enviar("/auth/login", """
            {"email":"ana@mail.com","contrasenia":"clave1234"}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("el-access"))
            .andExpect(jsonPath("$.refreshToken").value("el-refresh"))
            .andExpect(jsonPath("$.rol").value("PRESTADOR"));
    }

    @Test
    void loginConEmailConMayusculasYEspaciosLlegaRecortadoAlServicio() throws Exception {
        when(sesion.iniciar(any(LoginRequest.class))).thenReturn(new TokensResponse("a", "r", Rol.CLIENTE));

        enviar("/auth/login", """
            {"email":" Ana@Mail.com ","contrasenia":"clave1234"}
            """)
            .andExpect(status().isOk());

        ArgumentCaptor<LoginRequest> recibido = ArgumentCaptor.forClass(LoginRequest.class);
        verify(sesion).iniciar(recibido.capture());
        assertThat(recibido.getValue().email()).isEqualTo("Ana@Mail.com");
    }

    @Test
    void loginSinCuerpoDa400PorqueEsPublicoYNoPorFaltaDeToken() throws Exception {
        mvc.perform(post(CONTEXTO + "/auth/login").contextPath(CONTEXTO))
            .andExpect(status().isBadRequest());
        verify(sesion, never()).iniciar(any());
    }

    @Test
    void loginSinContraseniaOConEmailInvalidoDa400() throws Exception {
        enviar("/auth/login", """
            {"email":"ana@mail.com"}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("contrasenia"));
        enviar("/auth/login", """
            {"email":"no-es-un-mail","contrasenia":"clave1234"}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("email"));
    }

    @Test
    void loginConCamposVaciosDevuelveLosDetallesEnEspaniol() throws Exception {
        enviar("/auth/login", """
            {"email":"","contrasenia":""}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[?(@.campo=='email')].detalle").value(hasItem("es obligatorio")))
            .andExpect(jsonPath("$.errores[?(@.campo=='contrasenia')].detalle").value(hasItem("es obligatorio")));
    }

    @Test
    void credencialesInvalidasDa401ConElCodigo() throws Exception {
        when(sesion.iniciar(any(LoginRequest.class))).thenThrow(new NegocioException(
            HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "El correo o la contraseña no son correctos."));

        enviar("/auth/login", """
            {"email":"ana@mail.com","contrasenia":"mala"}
            """)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
            .andExpect(jsonPath("$.mensaje").value("El correo o la contraseña no son correctos."));
    }

    @Test
    void cuentaSuspendidaDa403ConElCodigo() throws Exception {
        when(sesion.iniciar(any(LoginRequest.class))).thenThrow(
            new NegocioException(HttpStatus.FORBIDDEN, "CUENTA_SUSPENDIDA", "Tu cuenta está suspendida."));

        enviar("/auth/login", """
            {"email":"ana@mail.com","contrasenia":"clave1234"}
            """)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("CUENTA_SUSPENDIDA"));
    }

    // --- A3 refresh ---

    @Test
    void refreshConCuerpoValidoDa200ConLosTokensNuevos() throws Exception {
        when(sesion.renovar(any(RefreshRequest.class)))
            .thenReturn(new TokensResponse("nuevo-access", "nuevo-refresh", Rol.CLIENTE));

        enviar("/auth/refresh", """
            {"refreshToken":"el-viejo"}
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("nuevo-access"))
            .andExpect(jsonPath("$.refreshToken").value("nuevo-refresh"))
            .andExpect(jsonPath("$.rol").value("CLIENTE"));
    }

    @Test
    void refreshSinCuerpoOConTokenVacioDa400() throws Exception {
        mvc.perform(post(CONTEXTO + "/auth/refresh").contextPath(CONTEXTO))
            .andExpect(status().isBadRequest());
        enviar("/auth/refresh", """
            {"refreshToken":"  "}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("refreshToken"));
        verify(sesion, never()).renovar(any());
    }

    @Test
    void refreshReusadoDa401RefreshInvalido() throws Exception {
        when(sesion.renovar(any(RefreshRequest.class))).thenThrow(
            new NegocioException(HttpStatus.UNAUTHORIZED, "REFRESH_INVALIDO", "Tu sesión venció. Ingresá de nuevo."));

        enviar("/auth/refresh", """
            {"refreshToken":"ya-usado"}
            """)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("REFRESH_INVALIDO"));
    }

    @Test
    void recuperarEsPublicoDa204YRecortaElEmail() throws Exception {
        enviar("/auth/recuperar", """
            {"email":" Ana@Mail.com "}
            """)
            .andExpect(status().isNoContent());

        ArgumentCaptor<RecuperarRequest> recibido = ArgumentCaptor.forClass(RecuperarRequest.class);
        verify(recuperacion).solicitar(recibido.capture());
        assertThat(recibido.getValue().email()).isEqualTo("Ana@Mail.com");
    }

    @Test
    void recuperarConEmailInvalidoDa400() throws Exception {
        enviar("/auth/recuperar", """
            {"email":"no-es-un-mail"}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("email"));
        verify(recuperacion, never()).solicitar(any());
    }

    @Test
    void confirmarEsPublicoDa204() throws Exception {
        enviar("/auth/recuperar/confirmar", """
            {"email":" ana@mail.com ","codigo":"123456","contraseniaNueva":"nueva-clave9"}
            """)
            .andExpect(status().isNoContent());

        ArgumentCaptor<ConfirmarRecuperacionRequest> recibido = ArgumentCaptor.forClass(ConfirmarRecuperacionRequest.class);
        verify(recuperacion).confirmar(recibido.capture());
        assertThat(recibido.getValue().email()).isEqualTo("ana@mail.com");
        assertThat(recibido.getValue().codigo()).isEqualTo("123456");
    }

    @Test
    void confirmarConCodigoQueNoSonSeisDigitosDa400EnCodigo() throws Exception {
        enviar("/auth/recuperar/confirmar", """
            {"email":"ana@mail.com","codigo":"12a","contraseniaNueva":"nueva-clave9"}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"))
            .andExpect(jsonPath("$.errores[0].campo").value("codigo"))
            .andExpect(jsonPath("$.errores[0].detalle").value("son 6 dígitos"));
        verify(recuperacion, never()).confirmar(any());
    }

    @Test
    void confirmarConContraseniaDebilDa400EnContraseniaNueva() throws Exception {
        enviar("/auth/recuperar/confirmar", """
            {"email":"ana@mail.com","codigo":"123456","contraseniaNueva":"abcdefgh"}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores[0].campo").value("contraseniaNueva"));
    }

    @Test
    void confirmarConCodigoIncorrectoDa400CodigoInvalido() throws Exception {
        org.mockito.Mockito.doThrow(new NegocioException(HttpStatus.BAD_REQUEST, "CODIGO_INVALIDO",
            "El código no es válido o venció. Pedí uno nuevo."))
            .when(recuperacion).confirmar(any(ConfirmarRecuperacionRequest.class));

        enviar("/auth/recuperar/confirmar", """
            {"email":"ana@mail.com","codigo":"123456","contraseniaNueva":"nueva-clave9"}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
    }
}
