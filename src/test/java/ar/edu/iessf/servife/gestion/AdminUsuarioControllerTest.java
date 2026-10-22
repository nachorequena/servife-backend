package ar.edu.iessf.servife.gestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.EscritorDeErrores;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.config.CorsConfig;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.SeguridadConfig;
import ar.edu.iessf.servife.gestion.controller.AdminUsuarioController;
import ar.edu.iessf.servife.gestion.dto.CambioDeEstadoDeCuentaRequest;
import ar.edu.iessf.servife.gestion.dto.UsuarioAdminResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeUsuarios;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;

/** E3, E4, E13, E14 y E15: roles, validación del pedido, códigos HTTP y formato. La lógica va en ServicioDeUsuariosTest. */
@WebMvcTest(controllers = AdminUsuarioController.class)
@Import({SeguridadConfig.class, JwtConfig.class, CorsConfig.class, EscritorDeErrores.class, UsuarioActual.class})
@TestPropertySource(properties = {
    "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres",
    "servife.jwt.duracion-access=15m",
    "servife.jwt.duracion-refresh=7d",
    "servife.cors.origenes-permitidos=http://localhost:8081"
})
class AdminUsuarioControllerTest {

    private static final String CONTEXTO = "/api/v1";
    private static final String RUTA = CONTEXTO + "/admin/usuarios";
    private static final UUID GESTOR = UUID.fromString("7f1c2d4e-0000-4000-8000-0000000000aa");
    private static final UUID USUARIO = UUID.fromString("7f1c2d4e-0000-4000-8000-0000000000cc");
    private static final UsuarioAdminResponse ANA = new UsuarioAdminResponse(USUARIO, Rol.CLIENTE, "Ana", "ana@mail.com",
        "342555", EstadoCuenta.ACTIVA, null, null, Instant.parse("2026-10-01T10:00:00Z"));

    @Autowired private MockMvc mvc;
    @MockitoBean private ServicioDeUsuarios servicio;

    private static JwtRequestPostProcessor como(String rol) {
        UUID sujeto = "GESTOR".equals(rol) ? GESTOR : UUID.randomUUID();
        return jwt().jwt(j -> j.subject(sujeto.toString()).claim(JwtConfig.CLAIM_ROL, rol))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private static MockHttpServletRequestBuilder conJson(MockHttpServletRequestBuilder pedido, String cuerpo) {
        return pedido.contextPath(CONTEXTO).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static List<MockHttpServletRequestBuilder> todos() {
        return List.of(
            get(RUTA).contextPath(CONTEXTO),
            conJson(post(RUTA), "{\"nombreApellido\":\"G\",\"email\":\"g@mail.com\",\"contrasenia\":\"clave1234\"}"),
            conJson(put(RUTA + "/" + USUARIO), "{\"nombreApellido\":\"Ana\",\"email\":\"ana@mail.com\"}"),
            conJson(patch(RUTA + "/" + USUARIO + "/estado"), "{\"estado\":\"SUSPENDIDA\",\"motivo\":\"x\"}"),
            delete(RUTA + "/" + USUARIO).contextPath(CONTEXTO));
    }

    @Test
    void sinTokenEs401() throws Exception {
        for (MockHttpServletRequestBuilder pedido : todos()) {
            mvc.perform(pedido).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void clienteYPrestadorReciben403() throws Exception {
        for (String rol : List.of("CLIENTE", "PRESTADOR")) {
            for (MockHttpServletRequestBuilder pedido : todos()) {
                mvc.perform(pedido.with(como(rol))).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void listarPasaLosFiltrosYDevuelveLaPagina() throws Exception {
        when(servicio.listar(eq(Rol.PRESTADOR), eq(EstadoCuenta.SUSPENDIDA), eq("ana"), any()))
            .thenReturn(new Pagina<>(List.of(ANA), 0, 20, 1, 1));

        mvc.perform(get(RUTA + "?rol=PRESTADOR&estado=SUSPENDIDA&texto=ana").contextPath(CONTEXTO).with(como("GESTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido[0].uuid").value(USUARIO.toString()))
            .andExpect(jsonPath("$.contenido[0].rol").value("CLIENTE"))
            .andExpect(jsonPath("$.contenido[0].estadoCuenta").value("ACTIVA"))
            .andExpect(jsonPath("$.contenido[0].contrasenia").doesNotExist())
            .andExpect(jsonPath("$.contenido[0].id").doesNotExist())
            .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void filtrosOPaginacionInvalidosSon400() throws Exception {
        for (String consulta : List.of("rol=ADMIN", "estado=BORRADA", "page=-1", "size=51")) {
            mvc.perform(get(RUTA + "?" + consulta).contextPath(CONTEXTO).with(como("GESTOR")))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void crearDevuelve201ConLocation() throws Exception {
        when(servicio.crearGestor(any())).thenReturn(ANA);

        mvc.perform(conJson(post(RUTA), "{\"nombreApellido\":\"Gestora\",\"email\":\" g@mail.com \",\"contrasenia\":\"clave1234\"}")
                .with(como("GESTOR")))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost" + RUTA + "/" + USUARIO))
            .andExpect(jsonPath("$.uuid").value(USUARIO.toString()));
    }

    @Test
    void crearConDatosInvalidosEs400ConLosCampos() throws Exception {
        mvc.perform(conJson(post(RUTA), "{\"nombreApellido\":\"\",\"email\":\"no-es-correo\",\"contrasenia\":\"corta\"}")
                .with(como("GESTOR")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDACION"))
            .andExpect(jsonPath("$.errores.length()").value(3));
        verifyNoInteractions(servicio);
    }

    @Test
    void emailRepetidoEs409() throws Exception {
        when(servicio.crearGestor(any())).thenThrow(new ConflictoException("EMAIL_YA_REGISTRADO", "Ese correo ya tiene una cuenta."));

        mvc.perform(conJson(post(RUTA), "{\"nombreApellido\":\"G\",\"email\":\"g@mail.com\",\"contrasenia\":\"clave1234\"}")
                .with(como("GESTOR")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("EMAIL_YA_REGISTRADO"));
    }

    @Test
    void actualizarValidaElCuerpo() throws Exception {
        for (String cuerpo : List.of("{\"email\":\"a@mail.com\"}", "{\"nombreApellido\":\"Ana\",\"email\":\"x\"}",
                "{\"nombreApellido\":\"Ana\",\"email\":\"a@mail.com\",\"telefono\":\"" + "1".repeat(31) + "\"}")) {
            mvc.perform(conJson(put(RUTA + "/" + USUARIO), cuerpo).with(como("GESTOR"))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void cambiarEstadoPasaElGestorAutenticado() throws Exception {
        when(servicio.cambiarEstado(eq(USUARIO), any(), eq(GESTOR))).thenReturn(ANA);

        mvc.perform(conJson(patch(RUTA + "/" + USUARIO + "/estado"), "{\"estado\":\"SUSPENDIDA\",\"motivo\":\"Reclamos\"}")
                .with(como("GESTOR")))
            .andExpect(status().isOk());
        verify(servicio).cambiarEstado(USUARIO, new CambioDeEstadoDeCuentaRequest(EstadoCuenta.SUSPENDIDA, "Reclamos"), GESTOR);
    }

    @Test
    void cambiarEstadoSinEstadoOConMotivoLargoEs400() throws Exception {
        for (String cuerpo : List.of("{}", "{\"estado\":\"BORRADA\"}",
                "{\"estado\":\"SUSPENDIDA\",\"motivo\":\"" + "x".repeat(256) + "\"}")) {
            mvc.perform(conJson(patch(RUTA + "/" + USUARIO + "/estado"), cuerpo).with(como("GESTOR")))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void eliminarDevuelve204() throws Exception {
        mvc.perform(delete(RUTA + "/" + USUARIO).contextPath(CONTEXTO).with(como("GESTOR")))
            .andExpect(status().isNoContent());
        verify(servicio).darDeBaja(USUARIO, GESTOR);
    }

    @Test
    void uuidMalFormadoEs400() throws Exception {
        for (MockHttpServletRequestBuilder pedido : List.of(
                conJson(put(RUTA + "/no-es-uuid"), "{\"nombreApellido\":\"Ana\",\"email\":\"a@mail.com\"}"),
                conJson(patch(RUTA + "/no-es-uuid/estado"), "{\"estado\":\"ACTIVA\"}"),
                delete(RUTA + "/no-es-uuid").contextPath(CONTEXTO))) {
            mvc.perform(pedido.with(como("GESTOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void listarSinFiltrosLlegaConNulos() throws Exception {
        when(servicio.listar(isNull(), isNull(), isNull(), any())).thenReturn(new Pagina<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get(RUTA).contextPath(CONTEXTO).with(como("GESTOR"))).andExpect(status().isOk());
    }
}
