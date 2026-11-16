package ar.edu.iessf.servife.identidad.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.DireccionCliente;
import ar.edu.iessf.servife.identidad.dto.DireccionRequest;
import ar.edu.iessf.servife.identidad.dto.DireccionResponse;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.DireccionClienteRepository;

/**
 * A10 a A14 · Direcciones guardadas del cliente. Reglas:
 * <ul>
 * <li>Siempre hay una sola principal mientras haya direcciones; la primera que se carga lo es.</li>
 * <li>Al borrar la principal, pasa a serlo la más nueva de las que quedan.</li>
 * <li>clientes.direccion es la copia de la principal (null si no hay): así A4 y la precarga del formulario no cambian.</li>
 * <li>La de otro cliente es 404 (.ai/07-security.md).</li>
 * </ul>
 */
@Service
public class ServicioDeDirecciones {

    /** Alias de la principal que se crea al cargar la dirección desde A6. */
    static final String ALIAS_POR_DEFECTO = "Casa";

    private final ClienteRepository clientes;
    private final DireccionClienteRepository direcciones;

    public ServicioDeDirecciones(ClienteRepository clientes, DireccionClienteRepository direcciones) {
        this.clientes = clientes;
        this.direcciones = direcciones;
    }

    /** A10. La principal primero. */
    @Transactional(readOnly = true)
    public List<DireccionResponse> listar(UUID uuidCliente) {
        return vigentes(cliente(uuidCliente)).stream().map(ServicioDeDirecciones::aRespuesta).toList();
    }

    /** A11. La primera dirección, o con principal = true, queda como principal. */
    @Transactional
    public DireccionResponse crear(UUID uuidCliente, DireccionRequest pedido) {
        Cliente cliente = cliente(uuidCliente);
        boolean esLaPrimera = principalDe(cliente).isEmpty();
        DireccionCliente nueva = direcciones.saveAndFlush(
            new DireccionCliente(cliente, pedido.alias().trim(), pedido.direccion().trim()));
        if (esLaPrimera || Boolean.TRUE.equals(pedido.principal())) {
            marcarComoPrincipal(cliente, nueva);
        }
        return aRespuesta(nueva);
    }

    /** A12. principal = true la marca; false no desmarca (siempre tiene que haber una: se elige otra con A14). */
    @Transactional
    public DireccionResponse actualizar(UUID uuidCliente, UUID uuidDireccion, DireccionRequest pedido) {
        Cliente cliente = cliente(uuidCliente);
        DireccionCliente direccion = buscar(cliente, uuidDireccion);
        direccion.editar(pedido.alias().trim(), pedido.direccion().trim());
        if (Boolean.TRUE.equals(pedido.principal())) {
            marcarComoPrincipal(cliente, direccion);
        }
        copiarPrincipal(cliente);
        return aRespuesta(direccion);
    }

    /** A13. Baja lógica. Si era la principal, pasa a serlo la más nueva de las que quedan. */
    @Transactional
    public void eliminar(UUID uuidCliente, UUID uuidDireccion) {
        Cliente cliente = cliente(uuidCliente);
        darDeBaja(cliente, buscar(cliente, uuidDireccion));
    }

    /** A14. */
    @Transactional
    public DireccionResponse marcarPrincipal(UUID uuidCliente, UUID uuidDireccion) {
        Cliente cliente = cliente(uuidCliente);
        DireccionCliente direccion = buscar(cliente, uuidDireccion);
        marcarComoPrincipal(cliente, direccion);
        return aRespuesta(direccion);
    }

    /**
     * A6 sobre un cliente: la dirección del perfil es la principal. Con texto, edita la principal (o crea
     * "Casa" si no tiene); en blanco, borra la principal como A13. Corre dentro de la transacción de A6.
     */
    @Transactional
    public void sincronizarDesdePerfil(Cliente cliente, String direccion) {
        Optional<DireccionCliente> principal = principalDe(cliente);
        if (direccion == null) {
            principal.ifPresentOrElse(p -> darDeBaja(cliente, p), () -> cliente.setDireccion(null));
            return;
        }
        if (principal.isPresent()) {
            principal.get().setDireccion(direccion);
        } else {
            marcarComoPrincipal(cliente, direcciones.saveAndFlush(new DireccionCliente(cliente, ALIAS_POR_DEFECTO, direccion)));
        }
        copiarPrincipal(cliente);
    }

    private void darDeBaja(Cliente cliente, DireccionCliente direccion) {
        boolean eraPrincipal = direccion.isPrincipal();
        direccion.setPrincipal(false);
        direccion.darDeBaja();
        direcciones.flush();
        if (eraPrincipal) {
            vigentes(cliente).stream().findFirst().ifPresent(siguiente -> marcarComoPrincipal(cliente, siguiente));
        }
        copiarPrincipal(cliente);
    }

    /** Primero desmarca la anterior y baja a la base: el índice único no admite dos principales ni un instante. */
    private void marcarComoPrincipal(Cliente cliente, DireccionCliente nueva) {
        principalDe(cliente).filter(actual -> !actual.getId().equals(nueva.getId())).ifPresent(actual -> {
            actual.setPrincipal(false);
            direcciones.flush();
        });
        nueva.setPrincipal(true);
        direcciones.flush();
        copiarPrincipal(cliente);
    }

    private void copiarPrincipal(Cliente cliente) {
        cliente.setDireccion(principalDe(cliente).map(DireccionCliente::getDireccion).orElse(null));
    }

    private Optional<DireccionCliente> principalDe(Cliente cliente) {
        return direcciones.findByClienteAndPrincipalTrueAndEliminadoEnIsNull(cliente);
    }

    private List<DireccionCliente> vigentes(Cliente cliente) {
        return direcciones.findByClienteAndEliminadoEnIsNullOrderByPrincipalDescCreadoEnDesc(cliente);
    }

    private DireccionCliente buscar(Cliente cliente, UUID uuidDireccion) {
        return direcciones.findByUuidAndClienteAndEliminadoEnIsNull(uuidDireccion, cliente)
            .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos esa dirección."));
    }

    private Cliente cliente(UUID uuidCliente) {
        return clientes.findByUuidAndEliminadoEnIsNull(uuidCliente)
            .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos tu cuenta."));
    }

    private static DireccionResponse aRespuesta(DireccionCliente direccion) {
        return new DireccionResponse(direccion.getUuid(), direccion.getAlias(), direccion.getDireccion(), direccion.isPrincipal());
    }
}
