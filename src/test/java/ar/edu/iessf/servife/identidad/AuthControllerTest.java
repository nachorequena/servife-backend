package ar.edu.iessf.servife.identidad;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;
import ar.edu.iessf.servife.identidad.controller.AuthController;
import ar.edu.iessf.servife.identidad.dto.RegistroRequest;
import ar.edu.iessf.servife.identidad.dto.UsuarioResponse;
import ar.edu.iessf.servife.identidad.service.ServicioDeRegistro;

/** Endpoints de /auth (crece en las Tasks 7 a 9). */
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
            .thenThrow(new ConflictoException("EMAIL_YA_REGISTRADO", "Ese correo ya tiene una cuenta."));

        registrar(cuerpo("CLIENTE", "clave1234"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("EMAIL_YA_REGISTRADO"));
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
}
