package ar.edu.iessf.servife.identidad.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.domain.Gestor;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.dto.ActualizarUsuarioRequest;
import ar.edu.iessf.servife.identidad.dto.CambiarContraseniaRequest;
import ar.edu.iessf.servife.identidad.dto.UsuarioResponse;
import ar.edu.iessf.servife.identidad.mapper.UsuarioMapper;

/** A4 (sesión), A6 (datos de la cuenta) y A7 (cambio de contraseña) · CU02, CU03. Nunca loguea contraseñas. */
@Service
public class ServicioDeCuenta {

    private final Cuentas cuentas;
    private final UsuarioMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final ServicioDeTokens tokens;

    public ServicioDeCuenta(Cuentas cuentas, UsuarioMapper mapper, PasswordEncoder passwordEncoder,
        ServicioDeTokens tokens) {
        this.cuentas = cuentas;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    /** A4. Cuenta inexistente o dada de baja con token todavía válido: 404. */
    @Transactional(readOnly = true)
    public UsuarioResponse obtener(UUID uuid, Rol rol) {
        return mapper.aRespuesta(buscar(uuid, rol));
    }

    /**
     * A6. Recorta los textos; teléfono y dirección en blanco se guardan como null. El gestor no tiene
     * teléfono, dirección ni fecha de nacimiento: si los manda, 400 en ese campo.
     */
    @Transactional
    public UsuarioResponse actualizar(UUID uuid, Rol rol, ActualizarUsuarioRequest pedido) {
        Cuenta cuenta = buscar(uuid, rol);
        String nombre = pedido.nombreApellido().trim();
        String telefono = aNuloSiVacio(pedido.telefono());
        String direccion = aNuloSiVacio(pedido.direccion());
        switch (cuenta) {
            case Cliente c -> {
                c.setNombreApellido(nombre);
                c.setTelefono(telefono);
                c.setDireccion(direccion);
                c.setFecNacimiento(pedido.fecNacimiento());
            }
            case Prestador p -> {
                p.setNombreApellido(nombre);
                p.setTelefono(telefono);
                p.setDireccion(direccion);
                p.setFecNacimiento(pedido.fecNacimiento());
            }
            case Gestor g -> {
                rechazarSiHay("telefono", telefono);
                rechazarSiHay("direccion", direccion);
                rechazarSiHay("fecNacimiento", pedido.fecNacimiento());
                g.setNombreApellido(nombre);
            }
            default -> throw new IllegalStateException("Tipo de cuenta desconocido");
        }
        return mapper.aRespuesta(cuenta);
    }

    /** A7. Con la actual incorrecta, 400 en contraseniaActual; si cambia, revoca todos los refresh. */
    @Transactional
    public void cambiarContrasenia(UUID uuid, Rol rol, CambiarContraseniaRequest pedido) {
        Cuenta cuenta = buscar(uuid, rol);
        if (!passwordEncoder.matches(pedido.contraseniaActual(), cuenta.getContrasenia())) {
            throw new ValidacionException("contraseniaActual", "no coincide");
        }
        cuenta.cambiarContrasenia(passwordEncoder.encode(pedido.contraseniaNueva()));
        tokens.revocarTodos(uuid);
    }

    private Cuenta buscar(UUID uuid, Rol rol) {
        return cuentas.buscarPorUuid(uuid, rol)
            .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos tu cuenta."));
    }

    private static String aNuloSiVacio(String texto) {
        if (texto == null) {
            return null;
        }
        String recortado = texto.trim();
        return recortado.isEmpty() ? null : recortado;
    }

    private static void rechazarSiHay(String campo, Object valor) {
        if (valor != null) {
            throw new ValidacionException(campo, "los gestores no tienen este dato");
        }
    }
}
