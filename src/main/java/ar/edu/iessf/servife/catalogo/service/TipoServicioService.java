package ar.edu.iessf.servife.catalogo.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.IconosDeServicio;
import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.dto.TipoServicioRequest;
import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.ErrorCampo;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.identidad.domain.Gestor;
import ar.edu.iessf.servife.identidad.repository.GestorRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;

/** Tipos de servicio: B1 (listar) y la gestión del gestor, B2 a B4 (CU13). */
@Service
public class TipoServicioService {

    private final TipoServicioRepository repositorio;
    private final PrestadorRepository prestadores;
    private final GestorRepository gestores;
    private final UsuarioActual usuarioActual;

    public TipoServicioService(TipoServicioRepository repositorio, PrestadorRepository prestadores,
            GestorRepository gestores, UsuarioActual usuarioActual) {
        this.repositorio = repositorio;
        this.prestadores = prestadores;
        this.gestores = gestores;
        this.usuarioActual = usuarioActual;
    }

    /** Tipos de servicio no dados de baja, ordenados por nombre. */
    @Transactional(readOnly = true)
    public List<TipoServicioResponse> listarActivos() {
        return repositorio.findByEliminadoEnIsNullOrderByNombre().stream().map(TipoServicioService::aRespuesta).toList();
    }

    /**
     * B2: crea el tipo. Si el nombre (sin mayúsculas) es el de uno dado de baja, lo reactiva con los datos nuevos.
     * 409 TIPO_SERVICIO_DUPLICADO si coincide con uno activo.
     */
    @Transactional
    public TipoServicioResponse crear(TipoServicioRequest pedido) {
        String nombre = pedido.nombre().trim();
        validarIcono(pedido.icono());
        Gestor gestor = gestorActual();
        Optional<TipoServicio> existente = repositorio.findByNombreIgnoreCase(nombre);
        try {
            if (existente.isPresent() && !existente.get().estaEliminado()) {
                throw duplicado();
            }
            TipoServicio tipo;
            if (existente.isPresent()) {
                tipo = existente.get();
                tipo.reactivar(nombre, pedido.icono(), pedido.requiereMatricula(), gestor);
            } else {
                tipo = new TipoServicio(nombre, pedido.icono(), pedido.requiereMatricula(), gestor);
            }
            return aRespuesta(repositorio.saveAndFlush(tipo));
        } catch (DataIntegrityViolationException e) {
            throw traducir(e);
        }
    }

    /** B3: renombra o cambia el ícono y la matrícula. 404 si no existe o está dado de baja. */
    @Transactional
    public TipoServicioResponse actualizar(UUID uuid, TipoServicioRequest pedido) {
        TipoServicio tipo = buscarActivo(uuid);
        String nombre = pedido.nombre().trim();
        validarIcono(pedido.icono());
        // Cualquier otra fila con ese nombre (activa o dada de baja) es conflicto: no se fusionan filas.
        Optional<TipoServicio> otro = repositorio.findByNombreIgnoreCase(nombre);
        if (otro.isPresent() && !otro.get().getUuid().equals(uuid)) {
            throw duplicado();
        }
        tipo.renombrar(nombre);
        tipo.cambiarIcono(pedido.icono());
        tipo.cambiarRequiereMatricula(pedido.requiereMatricula());
        try {
            return aRespuesta(repositorio.saveAndFlush(tipo));
        } catch (DataIntegrityViolationException e) {
            throw traducir(e);
        }
    }

    /** B4: baja lógica. 409 TIPO_SERVICIO_EN_USO si hay prestadores no dados de baja con ese tipo. */
    @Transactional
    public void eliminar(UUID uuid) {
        TipoServicio tipo = buscarActivo(uuid);
        if (prestadores.existsByTipoServicioAndEliminadoEnIsNull(tipo)) {
            throw new ConflictoException("TIPO_SERVICIO_EN_USO",
                "No se puede eliminar: hay prestadores con este tipo de servicio.");
        }
        tipo.darDeBaja();
        repositorio.save(tipo);
    }

    private TipoServicio buscarActivo(UUID uuid) {
        return repositorio.findByUuidAndEliminadoEnIsNull(uuid)
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));
    }

    private Gestor gestorActual() {
        return gestores.findByUuidAndEliminadoEnIsNull(usuarioActual.uuid())
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));
    }

    private static void validarIcono(String icono) {
        if (!IconosDeServicio.PERMITIDOS.contains(icono)) {
            throw new ValidacionException("icono", "no es un ícono permitido");
        }
    }

    private static ConflictoException duplicado() {
        return new ConflictoException("TIPO_SERVICIO_DUPLICADO", "Ya existe un tipo de servicio con ese nombre.",
            new ErrorCampo("nombre", "ya existe"));
    }

    /** Dos altas simultáneas con el mismo nombre: gana la primera. Cualquier otra violación es un bug. */
    private static RuntimeException traducir(DataIntegrityViolationException e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException v
                    && ("tipos_servicio_nombre_key".equals(v.getConstraintName())
                        || "uq_tipos_servicio_nombre_lower".equals(v.getConstraintName()))) {
                return duplicado();
            }
        }
        return e;
    }

    private static TipoServicioResponse aRespuesta(TipoServicio t) {
        return new TipoServicioResponse(t.getUuid(), t.getNombre(), t.getIcono(), t.isRequiereMatricula());
    }
}
