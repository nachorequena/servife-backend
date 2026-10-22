package ar.edu.iessf.servife.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Gestor;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.dto.RegistroRequest;
import ar.edu.iessf.servife.identidad.dto.UsuarioResponse;
import ar.edu.iessf.servife.identidad.mapper.UsuarioMapper;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.identidad.service.Cuentas;
import ar.edu.iessf.servife.identidad.service.ServicioDeRegistro;

@ExtendWith(MockitoExtension.class)
class ServicioDeRegistroTest {

    private static final String CLAVE = "clave1234";
    private static final String HASH = "$2a$10$hash-de-prueba";

    @Mock private Cuentas cuentas;
    @Mock private ClienteRepository clientes;
    @Mock private PrestadorRepository prestadores;
    @Mock private TipoServicioRepository tiposServicio;
    @Mock private PasswordEncoder passwordEncoder;

    private ServicioDeRegistro servicio;

    @BeforeEach
    void preparar() {
        servicio = new ServicioDeRegistro(cuentas, clientes, prestadores, tiposServicio, passwordEncoder,
            new UsuarioMapper());
        // Por defecto el email está libre en los tres roles; cada test pisa el que le importa.
        lenient().when(cuentas.buscarPorEmailConBajas(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void clienteNuevoSeGuardaConEmailNormalizadoYContraseniaHasheada() {
        when(passwordEncoder.encode(CLAVE)).thenReturn(HASH);
        when(clientes.saveAndFlush(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponse respuesta = servicio.registrar(
            new RegistroRequest("CLIENTE", "Ana Pérez", " Ana@Mail.com ", CLAVE, null));

        ArgumentCaptor<Cliente> guardado = ArgumentCaptor.forClass(Cliente.class);
        verify(clientes).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getEmail()).isEqualTo("ana@mail.com");
        assertThat(guardado.getValue().getContrasenia()).isEqualTo(HASH).isNotEqualTo(CLAVE);
        verify(passwordEncoder).encode(CLAVE);
        assertThat(respuesta.rol()).isEqualTo(Rol.CLIENTE);
        assertThat(respuesta.email()).isEqualTo("ana@mail.com");
        assertThat(respuesta.nombreApellido()).isEqualTo("Ana Pérez");
        assertThat(respuesta.estadoValidacion()).isNull();
    }

    @Test
    void clienteConIdTipoServicioLoIgnora() {
        when(passwordEncoder.encode(CLAVE)).thenReturn(HASH);
        when(clientes.saveAndFlush(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        servicio.registrar(new RegistroRequest("CLIENTE", "Ana", "ana@mail.com", CLAVE, UUID.randomUUID()));

        verify(clientes).saveAndFlush(any(Cliente.class));
        verify(tiposServicio, never()).findByUuidAndEliminadoEnIsNull(any());
        verify(prestadores, never()).saveAndFlush(any());
    }

    @Test
    void prestadorQuedaPendienteConElTipoAsignado() {
        UUID idTipo = UUID.randomUUID();
        TipoServicio gas = mock(TipoServicio.class);
        when(tiposServicio.findByUuidAndEliminadoEnIsNull(idTipo)).thenReturn(Optional.of(gas));
        when(passwordEncoder.encode(CLAVE)).thenReturn(HASH);
        when(prestadores.saveAndFlush(any(Prestador.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponse respuesta = servicio.registrar(
            new RegistroRequest("PRESTADOR", "Pablo Gas", "Pablo@Mail.com", CLAVE, idTipo));

        ArgumentCaptor<Prestador> guardado = ArgumentCaptor.forClass(Prestador.class);
        verify(prestadores).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getTipoServicio()).isSameAs(gas);
        assertThat(guardado.getValue().getEstadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
        assertThat(guardado.getValue().getEmail()).isEqualTo("pablo@mail.com");
        assertThat(guardado.getValue().getContrasenia()).isEqualTo(HASH);
        assertThat(respuesta.rol()).isEqualTo(Rol.PRESTADOR);
        assertThat(respuesta.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
    }

    @Test
    void emailDeUnGestorDa409() {
        when(cuentas.buscarPorEmailConBajas("gaby@mail.com", Rol.GESTOR)).thenReturn(Optional.of(mock(Gestor.class)));

        assertThatThrownBy(() -> servicio.registrar(
            new RegistroRequest("CLIENTE", "Gaby", "Gaby@mail.com", CLAVE, null)))
            .isInstanceOfSatisfying(ConflictoException.class, e -> {
                assertThat(e.getCodigo()).isEqualTo("EMAIL_YA_REGISTRADO");
                assertThat(e.getErrores()).containsExactly(new ar.edu.iessf.servife.common.error.ErrorCampo("email", "ya existe"));
                assertThat(e.getMessage()).isEqualTo("Ese correo ya tiene una cuenta.");
            });
        verify(clientes, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void emailDeUnaCuentaDelMismoRolDa409AunqueEsteDadaDeBaja() {
        when(cuentas.buscarPorEmailConBajas("ana@mail.com", Rol.CLIENTE)).thenReturn(Optional.of(mock(Cliente.class)));

        assertThatThrownBy(() -> servicio.registrar(new RegistroRequest("CLIENTE", "Ana", "ana@mail.com", CLAVE, null)))
            .isInstanceOfSatisfying(ConflictoException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("EMAIL_YA_REGISTRADO"));
        verify(clientes, never()).saveAndFlush(any());
    }

    // ─── La cuenta del otro rol del mismo email (D43) ──────────────────────

    private Cliente anaClienta(EstadoCuenta estado, boolean dadaDeBaja) {
        Cliente ana = mock(Cliente.class);
        when(ana.estaEliminado()).thenReturn(dadaDeBaja);
        if (!dadaDeBaja) {
            when(ana.getContrasenia()).thenReturn(HASH);
            lenient().when(ana.getEstadoCuenta()).thenReturn(estado);
        }
        when(cuentas.buscarPorEmailConBajas("ana@mail.com", Rol.CLIENTE)).thenReturn(Optional.of(ana));
        return ana;
    }

    private RegistroRequest anaPrestadora(String clave, UUID idTipo) {
        return new RegistroRequest("PRESTADOR", "Ana Pérez", "ana@mail.com", clave, idTipo);
    }

    @Test
    void conLaContraseniaDeLaCuentaDelOtroRolSeCreaLaSegunda() {
        UUID idTipo = UUID.randomUUID();
        anaClienta(EstadoCuenta.ACTIVA, false);
        when(passwordEncoder.matches(CLAVE, HASH)).thenReturn(true);
        when(tiposServicio.findByUuidAndEliminadoEnIsNull(idTipo)).thenReturn(Optional.of(mock(TipoServicio.class)));
        when(passwordEncoder.encode(CLAVE)).thenReturn(HASH);
        when(prestadores.saveAndFlush(any(Prestador.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponse respuesta = servicio.registrar(anaPrestadora(CLAVE, idTipo));

        assertThat(respuesta.rol()).isEqualTo(Rol.PRESTADOR);
        assertThat(respuesta.email()).isEqualTo("ana@mail.com");
    }

    @Test
    void conOtraContraseniaDa409ConElMismoMensaje() {
        anaClienta(EstadoCuenta.ACTIVA, false);
        when(passwordEncoder.matches("otra-clave1", HASH)).thenReturn(false);

        assertThatThrownBy(() -> servicio.registrar(anaPrestadora("otra-clave1", UUID.randomUUID())))
            .isInstanceOfSatisfying(ConflictoException.class, e -> {
                assertThat(e.getCodigo()).isEqualTo("EMAIL_YA_REGISTRADO");
                assertThat(e.getMessage()).isEqualTo("Ese correo ya tiene una cuenta.");
            });
        verify(prestadores, never()).saveAndFlush(any());
    }

    @Test
    void siLaCuentaDelOtroRolEstaDadaDeBajaDa409() {
        anaClienta(EstadoCuenta.ACTIVA, true);

        assertThatThrownBy(() -> servicio.registrar(anaPrestadora(CLAVE, UUID.randomUUID())))
            .isInstanceOfSatisfying(ConflictoException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("EMAIL_YA_REGISTRADO"));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void siLaCuentaDelOtroRolEstaSuspendidaDa403() {
        anaClienta(EstadoCuenta.SUSPENDIDA, false);
        when(passwordEncoder.matches(CLAVE, HASH)).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar(anaPrestadora(CLAVE, UUID.randomUUID())))
            .isInstanceOfSatisfying(NegocioException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(e.getCodigo()).isEqualTo("CUENTA_SUSPENDIDA");
            });
        verify(prestadores, never()).saveAndFlush(any());
    }

    @Test
    void siDosRegistrosSeCruzanElUniqueDeEmailDa409EnVezDe500() {
        when(passwordEncoder.encode(CLAVE)).thenReturn(HASH);
        when(clientes.saveAndFlush(any(Cliente.class))).thenThrow(violacion("clientes_email_key"));

        assertThatThrownBy(() -> servicio.registrar(
            new RegistroRequest("CLIENTE", "Ana", "ana@mail.com", CLAVE, null)))
            .isInstanceOfSatisfying(ConflictoException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("EMAIL_YA_REGISTRADO"));
    }

    @Test
    void otraViolacionDeIntegridadSeRelanzaTalCual() {
        when(passwordEncoder.encode(CLAVE)).thenReturn(HASH);
        DataIntegrityViolationException otra = violacion("clientes_uuid_key");
        when(clientes.saveAndFlush(any(Cliente.class))).thenThrow(otra);

        assertThatThrownBy(() -> servicio.registrar(
            new RegistroRequest("CLIENTE", "Ana", "ana@mail.com", CLAVE, null)))
            .isSameAs(otra);
    }

    @Test
    void contraseniaDeMasDe72BytesDaValidacionAunqueTenga70Caracteres() {
        String larga = "a1" + "ñ".repeat(68); // 70 caracteres, 138 bytes en UTF-8

        assertThatThrownBy(() -> servicio.registrar(
            new RegistroRequest("CLIENTE", "Ana", "ana@mail.com", larga, null)))
            .isInstanceOfSatisfying(ValidacionException.class, e -> {
                assertThat(e.getCampo()).isEqualTo("contrasenia");
                assertThat(e.getDetalle()).isEqualTo("es demasiado larga");
            });
        verify(passwordEncoder, never()).encode(any());
    }

    private static DataIntegrityViolationException violacion(String constraint) {
        return new DataIntegrityViolationException("violación",
            new ConstraintViolationException("violación", new SQLException("x"), constraint));
    }

    @Test
    void prestadorSinTipoDaValidacionSobreIdTipoServicio() {

        assertThatThrownBy(() -> servicio.registrar(
            new RegistroRequest("PRESTADOR", "Pablo", "pablo@mail.com", CLAVE, null)))
            .isInstanceOfSatisfying(ValidacionException.class, e -> {
                assertThat(e.getCampo()).isEqualTo("idTipoServicio");
                assertThat(e.getDetalle()).isEqualTo("es obligatorio para prestadores");
            });
        verify(prestadores, never()).saveAndFlush(any());
    }

    @Test
    void tipoInexistenteODadoDeBajaDaValidacion() {
        UUID idTipo = UUID.randomUUID();
        when(tiposServicio.findByUuidAndEliminadoEnIsNull(idTipo)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(
            new RegistroRequest("PRESTADOR", "Pablo", "pablo@mail.com", CLAVE, idTipo)))
            .isInstanceOfSatisfying(ValidacionException.class, e -> {
                assertThat(e.getCampo()).isEqualTo("idTipoServicio");
                assertThat(e.getDetalle()).isEqualTo("no existe");
            });
        verify(prestadores, never()).saveAndFlush(any());
    }
}
