package ar.edu.iessf.servife.identidad.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
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

    /** Un prestador nace PENDIENTE de validación; en un cliente, idTipoServicio se ignora. */
    @Transactional
    public UsuarioResponse registrar(RegistroRequest pedido) {
        String email = Cuentas.normalizar(pedido.email());
        if (cuentas.emailRegistrado(email)) {
            throw emailYaRegistrado();
        }
        boolean esPrestador = ROL_PRESTADOR.equals(pedido.rol());
        TipoServicio tipo = esPrestador ? buscarTipo(pedido) : null;
        Contrasenias.validarLargo("contrasenia", pedido.contrasenia());
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

    private TipoServicio buscarTipo(RegistroRequest pedido) {
        if (pedido.idTipoServicio() == null) {
            throw new ValidacionException("idTipoServicio", "es obligatorio para prestadores");
        }
        return tiposServicio.findByUuidAndEliminadoEnIsNull(pedido.idTipoServicio())
            .orElseThrow(() -> new ValidacionException("idTipoServicio", "no existe"));
    }

    private static ConflictoException emailYaRegistrado() {
        return new ConflictoException("EMAIL_YA_REGISTRADO", "Ese correo ya tiene una cuenta.");
    }
}
