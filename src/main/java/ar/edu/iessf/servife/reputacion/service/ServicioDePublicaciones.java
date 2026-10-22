package ar.edu.iessf.servife.reputacion.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.service.ServicioDeFichaDePrestador;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.paginacion.Paginacion;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.reputacion.domain.Archivo;
import ar.edu.iessf.servife.reputacion.domain.Publicacion;
import ar.edu.iessf.servife.reputacion.dto.PublicacionRequest;
import ar.edu.iessf.servife.reputacion.dto.PublicacionResponse;
import ar.edu.iessf.servife.reputacion.repository.ArchivoRepository;
import ar.edu.iessf.servife.reputacion.repository.PublicacionRepository;
import ar.edu.iessf.servife.reputacion.repository.ValoracionRepository;
import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import ar.edu.iessf.servife.solicitudes.repository.SolicitudRepository;

/**
 * Trabajos realizados (CU10, CU11): D3 y D8 (listar), D4 (publicar), D5 (editar) y D6 (baja lógica).
 * Una publicación puede vincular una solicitud propia FINALIZADA o VALORADA, una sola vez; la tarjeta
 * muestra la valoración de esa solicitud (D07). Recurso ajeno o dado de baja: 404.
 */
@Service
public class ServicioDePublicaciones {

    private static final Set<EstadoSolicitud> TERMINADAS = Set.of(EstadoSolicitud.FINALIZADA, EstadoSolicitud.VALORADA);
    private static final Sort MAS_RECIENTES = Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("uuid"));

    private final PublicacionRepository publicaciones;
    private final ValoracionRepository valoraciones;
    private final PrestadorRepository prestadores;
    private final SolicitudRepository solicitudes;
    private final ArchivoRepository archivos;
    private final ServicioDeFichaDePrestador fichas;
    private final UsuarioActual usuarioActual;

    public ServicioDePublicaciones(PublicacionRepository publicaciones, ValoracionRepository valoraciones,
            PrestadorRepository prestadores, SolicitudRepository solicitudes, ArchivoRepository archivos,
            ServicioDeFichaDePrestador fichas, UsuarioActual usuarioActual) {
        this.publicaciones = publicaciones;
        this.valoraciones = valoraciones;
        this.prestadores = prestadores;
        this.solicitudes = solicitudes;
        this.archivos = archivos;
        this.fichas = fichas;
        this.usuarioActual = usuarioActual;
    }

    /** D3. Solo de prestadores visibles (criterio de B6), si no 404. Sin uuidSolicitud. */
    @Transactional(readOnly = true)
    public Pagina<PublicacionResponse> listarDePrestador(UUID uuidPrestador, Integer page, Integer size) {
        Prestador prestador = fichas.visible(uuidPrestador);
        return listar(prestador, Paginacion.pedir(page, size, MAS_RECIENTES), false);
    }

    /** D8. Las propias, aunque el prestador todavía no esté aprobado. */
    @Transactional(readOnly = true)
    public Pagina<PublicacionResponse> listarMias(Integer page, Integer size) {
        return listar(prestadorActual(), Paginacion.pedir(page, size, MAS_RECIENTES), true);
    }

    /** D4. Valida solicitud e imágenes; el primer error corta. */
    @Transactional
    public PublicacionResponse crear(PublicacionRequest pedido) {
        Prestador prestador = prestadorActual();
        Solicitud solicitud = solicitudVinculable(pedido.uuidSolicitud(), prestador, null);
        List<Archivo> imagenes = imagenesValidas(pedido.imagenIds(), prestador);

        Publicacion publicacion = new Publicacion(prestador, solicitud, pedido.titulo().trim(),
            textoONulo(pedido.descripcion()), pedido.fechaTrabajo());
        guardar(publicacion, imagenes);
        return respuesta(publicacion, true);
    }

    /** D5. Reemplaza todo, imágenes incluidas: las que se sacan quedan sueltas y las borra la limpieza (D31). */
    @Transactional
    public PublicacionResponse actualizar(UUID uuid, PublicacionRequest pedido) {
        Prestador prestador = prestadorActual();
        Publicacion publicacion = propia(uuid, prestador);
        Solicitud solicitud = solicitudVinculable(pedido.uuidSolicitud(), prestador, publicacion.getId());
        // se sueltan primero para que las imágenes que se conservan no cuenten como "en uso"
        publicaciones.desvincularImagenes(publicacion.getId());
        List<Archivo> imagenes = imagenesValidas(pedido.imagenIds(), prestador);

        publicacion.actualizar(solicitud, pedido.titulo().trim(), textoONulo(pedido.descripcion()),
            pedido.fechaTrabajo());
        guardar(publicacion, imagenes);
        return respuesta(publicacion, true);
    }

    /** D6. Baja lógica: las imágenes siguen vinculadas, pero GET /archivos ya no las muestra a terceros (D32). */
    @Transactional
    public void eliminar(UUID uuid) {
        propia(uuid, prestadorActual()).darDeBaja();
    }

    private Pagina<PublicacionResponse> listar(Prestador prestador, Pageable pagina, boolean paraElDuenio) {
        Page<Publicacion> resultado = publicaciones.findByPrestadorAndEliminadoEnIsNull(prestador, pagina);
        List<Publicacion> contenido = resultado.getContent();
        Map<Long, List<UUID>> imagenes = imagenesDe(contenido);
        Map<Long, Integer> puntajes = puntajesDe(contenido);
        return Pagina.de(resultado, p -> aRespuesta(p, imagenes.getOrDefault(p.getId(), List.of()),
            p.getSolicitud() == null ? null : puntajes.get(p.getSolicitud().getId()), paraElDuenio));
    }

    private PublicacionResponse respuesta(Publicacion p, boolean paraElDuenio) {
        return aRespuesta(p, imagenesDe(List.of(p)).getOrDefault(p.getId(), List.of()),
            p.getSolicitud() == null ? null : puntajesDe(List.of(p)).get(p.getSolicitud().getId()), paraElDuenio);
    }

    private static PublicacionResponse aRespuesta(Publicacion p, List<UUID> imagenes, Integer puntaje,
            boolean paraElDuenio) {
        UUID uuidSolicitud = paraElDuenio && p.getSolicitud() != null ? p.getSolicitud().getUuid() : null;
        return new PublicacionResponse(p.getUuid(), p.getTitulo(), p.getDescripcion(), p.getFechaTrabajo(),
            imagenes, puntaje, uuidSolicitud, p.getCreadoEn());
    }

    /** Imágenes de varias publicaciones con una sola consulta, en el orden en que se subieron. */
    private Map<Long, List<UUID>> imagenesDe(List<Publicacion> lista) {
        if (lista.isEmpty()) {
            return Map.of();
        }
        return publicaciones.imagenesDe(lista.stream().map(Publicacion::getId).toList()).stream()
            .collect(Collectors.groupingBy(PublicacionRepository.ImagenDePublicacion::getIdPublicacion,
                LinkedHashMap::new,
                Collectors.mapping(PublicacionRepository.ImagenDePublicacion::getUuid, Collectors.toList())));
    }

    /** Puntaje de la valoración de cada solicitud vinculada, con una sola consulta. */
    private Map<Long, Integer> puntajesDe(List<Publicacion> lista) {
        List<Long> ids = lista.stream().map(Publicacion::getSolicitud).filter(Objects::nonNull)
            .map(Solicitud::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return valoraciones.puntajesDe(ids).stream()
            .collect(Collectors.toMap(ValoracionRepository.PuntajeDeSolicitud::getIdSolicitud,
                ValoracionRepository.PuntajeDeSolicitud::getPuntaje));
    }

    /**
     * Persiste la publicación y vincula las imágenes. Los índices únicos de V8 cubren la carrera de dos
     * pedidos simultáneos: se traducen al mismo 400 que la validación previa.
     */
    private void guardar(Publicacion publicacion, List<Archivo> imagenes) {
        try {
            publicaciones.saveAndFlush(publicacion);
            for (Archivo imagen : imagenes) {
                publicaciones.vincularImagen(publicacion.getId(), imagen.getId());
            }
        } catch (DataIntegrityViolationException e) {
            String mensaje = e.getMessage() == null ? "" : e.getMessage();
            if (mensaje.contains("uq_publicacion_imagenes_archivo")) {
                throw imagenInvalida();
            }
            if (mensaje.contains("uq_publicaciones_solicitud")) {
                throw solicitudYaPublicada();
            }
            throw e;
        }
    }

    /** null si no se pidió vincular; si no, una solicitud propia terminada que no esté en otra publicación. */
    private Solicitud solicitudVinculable(UUID uuidSolicitud, Prestador prestador, Long idPublicacion) {
        if (uuidSolicitud == null) {
            return null;
        }
        Solicitud solicitud = solicitudes.findByUuidAndEliminadoEnIsNull(uuidSolicitud)
            .filter(s -> s.getPrestador().getId().equals(prestador.getId()) && TERMINADAS.contains(s.getEstado()))
            .orElseThrow(() -> new ValidacionException("uuidSolicitud", "no es un trabajo terminado tuyo"));
        if (publicaciones.solicitudPublicadaEnOtra(solicitud, idPublicacion)) {
            throw solicitudYaPublicada();
        }
        return solicitud;
    }

    /** Cada imagen tiene que existir, ser de este prestador y no estar en otra publicación; sin repetidas. */
    private List<Archivo> imagenesValidas(List<UUID> uuids, Prestador prestador) {
        List<UUID> pedidas = uuids == null ? List.of() : uuids;
        if (new HashSet<>(pedidas).size() != pedidas.size()) {
            throw imagenInvalida();
        }
        List<Archivo> resultado = new ArrayList<>();
        for (UUID uuid : pedidas) {
            Archivo a = archivos.findByUuidAndEliminadoEnIsNull(uuid).orElseThrow(ServicioDePublicaciones::imagenInvalida);
            boolean propia = a.getRolPropietario() == Rol.PRESTADOR && a.getIdPropietario().equals(prestador.getId());
            if (!propia || publicaciones.imagenEnUso(a.getId())) {
                throw imagenInvalida();
            }
            resultado.add(a);
        }
        return resultado;
    }

    private Publicacion propia(UUID uuid, Prestador prestador) {
        return publicaciones.findByUuidAndEliminadoEnIsNull(uuid)
            .filter(p -> p.getPrestador().getId().equals(prestador.getId()))
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));
    }

    private Prestador prestadorActual() {
        return prestadores.findByUuidAndEliminadoEnIsNull(usuarioActual.uuid())
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));
    }

    private static ValidacionException imagenInvalida() {
        return new ValidacionException("imagenIds", "alguna imagen no es válida");
    }

    private static ValidacionException solicitudYaPublicada() {
        return new ValidacionException("uuidSolicitud", "ya tiene una publicación");
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
