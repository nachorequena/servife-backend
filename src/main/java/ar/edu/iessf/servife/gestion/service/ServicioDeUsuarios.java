package ar.edu.iessf.servife.gestion.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.gestion.dto.ActualizarUsuarioAdminRequest;
import ar.edu.iessf.servife.gestion.dto.CambioDeEstadoDeCuentaRequest;
import ar.edu.iessf.servife.gestion.dto.CrearGestorRequest;
import ar.edu.iessf.servife.gestion.dto.UsuarioAdminResponse;
import ar.edu.iessf.servife.gestion.repository.ListadoDeUsuariosRepository;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Gestor;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.GestorRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.identidad.service.Contrasenias;
import ar.edu.iessf.servife.identidad.service.Cuentas;
import ar.edu.iessf.servife.identidad.service.ServicioDeRegistro;
import ar.edu.iessf.servife.identidad.service.ServicioDeTokens;

/**
 * Gestión de usuarios del gestor · CU12 y sus extensiones CU15, CU16 y CU17 (D06): listar y filtrar
 * (E3), dar de alta un gestor (E13), editar nombre, email y teléfono (E14), suspender o reactivar con
 * aviso por correo (E4) y dar de baja (E15). Suspender y dar de baja revocan los refresh tokens; el
 * access token deja de servir en el acto por FiltroDeCuentaActiva. Un gestor no puede suspenderse ni
 * darse de baja a sí mismo.
 */
@Service
public class ServicioDeUsuarios {

    private static final int MAXIMO_TEXTO = 120;

    private final ListadoDeUsuariosRepository listado;
    private final Cuentas cuentas;
    private final ClienteRepository clientes;
    private final PrestadorRepository prestadores;
    private final GestorRepository gestores;
    private final PasswordEncoder encoder;
    private final ServicioDeTokens tokens;
    private final ApplicationEventPublisher eventos;

    public ServicioDeUsuarios(ListadoDeUsuariosRepository listado, Cuentas cuentas, ClienteRepository clientes,
            PrestadorRepository prestadores, GestorRepository gestores, PasswordEncoder encoder, ServicioDeTokens tokens,
            ApplicationEventPublisher eventos) {
        this.listado = listado;
        this.cuentas = cuentas;
        this.clientes = clientes;
        this.prestadores = prestadores;
        this.gestores = gestores;
        this.encoder = encoder;
        this.tokens = tokens;
        this.eventos = eventos;
    }

    /** E3: filtros opcionales; texto busca en nombre o email (sin distinguir mayúsculas) o por uuid exacto. */
    @Transactional(readOnly = true)
    public Pagina<UsuarioAdminResponse> listar(Rol rol, EstadoCuenta estado, String texto, Pageable pagina) {
        String limpio = texto == null || texto.isBlank() ? null : texto.trim();
        if (limpio != null && limpio.length() > MAXIMO_TEXTO) {
            throw new ValidacionException("texto", "es demasiado largo");
        }
        return listado.listar(new ListadoDeUsuariosRepository.Criterios(rol, estado, limpio, pagina.getPageNumber(),
            pagina.getPageSize()));
    }

    /** E13: alta de un gestor. 409 EMAIL_YA_REGISTRADO si el email ya lo usa cualquier cuenta, aunque esté dada de baja. */
    @Transactional
    public UsuarioAdminResponse crearGestor(CrearGestorRequest pedido) {
        Contrasenias.validarLargo("contrasenia", pedido.contrasenia());
        if (cuentas.emailRegistrado(pedido.email())) {
            throw ServicioDeRegistro.emailYaRegistrado();
        }
        Gestor gestor = new Gestor(pedido.nombreApellido().trim(), pedido.email(), encoder.encode(pedido.contrasenia()));
        return aRespuesta(guardar(gestor));
    }

    /** E14: nombre, email y teléfono. 404 si no existe o está dado de baja; 400 si se manda teléfono a un gestor. */
    @Transactional
    public UsuarioAdminResponse actualizar(UUID uuid, ActualizarUsuarioAdminRequest pedido) {
        Cuenta cuenta = buscar(uuid);
        String telefono = pedido.telefono() == null || pedido.telefono().isBlank() ? null : pedido.telefono().trim();
        if (cuenta instanceof Gestor && telefono != null) {
            throw new ValidacionException("telefono", "los gestores no tienen teléfono");
        }
        String email = Cuentas.normalizar(pedido.email());
        if (!email.equals(cuenta.getEmail()) && cuentas.emailRegistrado(email)) {
            throw ServicioDeRegistro.emailYaRegistrado();
        }
        cuenta.cambiarEmail(email);
        switch (cuenta) {
            case Cliente c -> {
                c.setNombreApellido(pedido.nombreApellido().trim());
                c.setTelefono(telefono);
            }
            case Prestador p -> {
                p.setNombreApellido(pedido.nombreApellido().trim());
                p.setTelefono(telefono);
            }
            case Gestor g -> g.setNombreApellido(pedido.nombreApellido().trim());
            default -> throw new IllegalStateException("Tipo de cuenta desconocido");
        }
        return aRespuesta(guardar(cuenta));
    }

    /**
     * E4: suspende (motivo obligatorio) o reactiva. Suspender revoca los refresh tokens. El correo al
     * usuario sale después del commit. 409 ESTADO_SIN_CAMBIOS si ya estaba en ese estado.
     */
    @Transactional
    public UsuarioAdminResponse cambiarEstado(UUID uuid, CambioDeEstadoDeCuentaRequest pedido, UUID uuidGestor) {
        noSobreLaPropiaCuenta(uuid, uuidGestor);
        Cuenta cuenta = buscar(uuid);
        String motivo = pedido.motivo() == null || pedido.motivo().isBlank() ? null : pedido.motivo().trim();
        if (pedido.estado() == EstadoCuenta.SUSPENDIDA && motivo == null) {
            throw new ValidacionException("motivo", "es obligatorio al suspender");
        }
        if (cuenta.getEstadoCuenta() == pedido.estado()) {
            throw new ConflictoException("ESTADO_SIN_CAMBIOS", pedido.estado() == EstadoCuenta.SUSPENDIDA
                ? "La cuenta ya está suspendida." : "La cuenta ya está activa.");
        }
        cuenta.setEstadoCuenta(pedido.estado());
        Cuenta guardada = guardar(cuenta);
        if (pedido.estado() == EstadoCuenta.SUSPENDIDA) {
            tokens.revocarTodos(uuid);
        }
        eventos.publishEvent(new EstadoDeCuentaCambiado(guardada.getEmail(), guardada.getNombreApellido(),
            pedido.estado(), motivo));
        return aRespuesta(guardada);
    }

    /** E15 · CU16: baja lógica y revocación de los refresh tokens. El email sigue reservado. 404 si no existe o ya está dado de baja. */
    @Transactional
    public void darDeBaja(UUID uuid, UUID uuidGestor) {
        noSobreLaPropiaCuenta(uuid, uuidGestor);
        Cuenta cuenta = buscar(uuid);
        cuenta.darDeBaja();
        guardar(cuenta);
        tokens.revocarTodos(uuid);
    }

    private static void noSobreLaPropiaCuenta(UUID uuid, UUID uuidGestor) {
        if (uuid.equals(uuidGestor)) {
            throw new ConflictoException("NO_SOBRE_LA_PROPIA_CUENTA",
                "No podés suspender ni dar de baja tu propia cuenta.");
        }
    }

    /** La cuenta no dada de baja con ese uuid, en cualquiera de las tres tablas. */
    private Cuenta buscar(UUID uuid) {
        for (Rol rol : Rol.values()) {
            Optional<Cuenta> cuenta = cuentas.buscarPorUuid(uuid, rol);
            if (cuenta.isPresent()) {
                return cuenta.get();
            }
        }
        throw new RecursoNoEncontradoException("No encontramos al usuario.");
    }

    /** Guarda con flush, para que la carrera de dos altas o ediciones con el mismo email también dé 409. */
    private Cuenta guardar(Cuenta cuenta) {
        try {
            return switch (cuenta) {
                case Cliente c -> clientes.saveAndFlush(c);
                case Prestador p -> prestadores.saveAndFlush(p);
                case Gestor g -> gestores.saveAndFlush(g);
                default -> throw new IllegalStateException("Tipo de cuenta desconocido");
            };
        } catch (DataIntegrityViolationException e) {
            if (ServicioDeRegistro.esViolacionDeEmail(e)) {
                throw ServicioDeRegistro.emailYaRegistrado();
            }
            throw e;
        }
    }

    private static UsuarioAdminResponse aRespuesta(Cuenta cuenta) {
        String telefono = null;
        String tipoServicio = null;
        EstadoValidacion validacion = null;
        if (cuenta instanceof Cliente c) {
            telefono = c.getTelefono();
        } else if (cuenta instanceof Prestador p) {
            telefono = p.getTelefono();
            tipoServicio = p.getTipoServicio().getNombre();
            validacion = p.getEstadoValidacion();
        }
        return new UsuarioAdminResponse(cuenta.getUuid(), cuenta.getRol(), cuenta.getNombreApellido(), cuenta.getEmail(),
            telefono, cuenta.getEstadoCuenta(), validacion, tipoServicio, ((EntidadBase) cuenta).getCreadoEn());
    }
}
