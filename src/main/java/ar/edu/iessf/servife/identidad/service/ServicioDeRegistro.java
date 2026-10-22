package ar.edu.iessf.servife.identidad.service;

import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.ErrorCampo;
import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.dto.RegistroRequest;
import ar.edu.iessf.servife.identidad.dto.UsuarioResponse;
import ar.edu.iessf.servife.identidad.mapper.UsuarioMapper;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;

/** A1 · CU01: alta de cliente o prestador. El registro público nunca crea gestores. */
@Service
public class ServicioDeRegistro {

    private static final String ROL_PRESTADOR = "PRESTADOR";

    private final Cuentas cuentas;
    private final ClienteRepository clientes;
    private final PrestadorRepository prestadores;
    private final TipoServicioRepository tiposServicio;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper mapper;

    public ServicioDeRegistro(Cuentas cuentas, ClienteRepository clientes, PrestadorRepository prestadores,
            TipoServicioRepository tiposServicio, PasswordEncoder passwordEncoder, UsuarioMapper mapper) {
        this.cuentas = cuentas;
        this.clientes = clientes;
        this.prestadores = prestadores;
        this.tiposServicio = tiposServicio;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    /**
     * Un prestador nace PENDIENTE de validación; en un cliente, idTipoServicio se ignora. El email puede
     * tener ya la cuenta del otro rol (D43): se crea solo si la contraseña es la de esa cuenta.
     */
    @Transactional
    public UsuarioResponse registrar(RegistroRequest pedido) {
        String email = Cuentas.normalizar(pedido.email());
        boolean esPrestador = ROL_PRESTADOR.equals(pedido.rol());
        Contrasenias.validarLargo("contrasenia", pedido.contrasenia());
        verificarEmailDisponible(email, esPrestador ? Rol.PRESTADOR : Rol.CLIENTE, pedido.contrasenia());
        TipoServicio tipo = esPrestador ? buscarTipo(pedido) : null;
        String hash = passwordEncoder.encode(pedido.contrasenia());
        try {
            Cuenta guardada = esPrestador
                ? prestadores.saveAndFlush(new Prestador(pedido.nombreApellido(), email, hash, tipo))
                : clientes.saveAndFlush(new Cliente(pedido.nombreApellido(), email, hash));
            return mapper.aRespuesta(guardada);
        } catch (DataIntegrityViolationException e) {
            // Dos registros simultáneos con el mismo email: gana el primero. Cualquier otra violación es un bug.
            if (esViolacionDeEmail(e)) {
                throw emailYaRegistrado();
            }
            throw e;
        }
    }

    /** Si la violación es del UNIQUE de email (clientes_email_key, prestadores_email_key, gestores_email_key). */
    public static boolean esViolacionDeEmail(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException v && v.getConstraintName() != null
                    && v.getConstraintName().endsWith("_email_key")) {
                return true;
            }
        }
        return false;
    }

    /**
     * D43. 409 EMAIL_YA_REGISTRADO, siempre con el mismo mensaje, si el email es de un gestor, de una
     * cuenta del mismo rol o de una del otro rol dada de baja, o si la contraseña no es la de la cuenta
     * del otro rol. Con la contraseña correcta y la otra cuenta suspendida, 403: no se esquiva una
     * suspensión sumando el otro rol.
     */
    private void verificarEmailDisponible(String email, Rol rol, String contrasenia) {
        Rol otroRol = rol == Rol.CLIENTE ? Rol.PRESTADOR : Rol.CLIENTE;
        if (cuentas.buscarPorEmailConBajas(email, Rol.GESTOR).isPresent()
                || cuentas.buscarPorEmailConBajas(email, rol).isPresent()) {
            throw emailYaRegistrado();
        }
        Optional<Cuenta> otra = cuentas.buscarPorEmailConBajas(email, otroRol);
        if (otra.isEmpty()) {
            return;
        }
        if (otra.get().estaEliminado() || !passwordEncoder.matches(contrasenia, otra.get().getContrasenia())) {
            throw emailYaRegistrado();
        }
        if (otra.get().getEstadoCuenta() == EstadoCuenta.SUSPENDIDA) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "CUENTA_SUSPENDIDA", "Tu cuenta está suspendida.");
        }
    }

    private TipoServicio buscarTipo(RegistroRequest pedido) {
        if (pedido.idTipoServicio() == null) {
            throw new ValidacionException("idTipoServicio", "es obligatorio para prestadores");
        }
        return tiposServicio.findByUuidAndEliminadoEnIsNull(pedido.idTipoServicio())
            .orElseThrow(() -> new ValidacionException("idTipoServicio", "no existe"));
    }

    /** 409 EMAIL_YA_REGISTRADO con el campo email; también lo usan el alta y la edición del gestor (E13, E14). */
    public static ConflictoException emailYaRegistrado() {
        return new ConflictoException("EMAIL_YA_REGISTRADO", "Ese correo ya tiene una cuenta.",
            new ErrorCampo("email", "ya existe"));
    }
}
