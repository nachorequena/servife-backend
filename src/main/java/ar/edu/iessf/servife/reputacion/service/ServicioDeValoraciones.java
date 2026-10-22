package ar.edu.iessf.servife.reputacion.service;

import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.service.ServicioDeFichaDePrestador;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.paginacion.Paginacion;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.reputacion.domain.Valoracion;
import ar.edu.iessf.servife.reputacion.dto.ValoracionRequest;
import ar.edu.iessf.servife.reputacion.dto.ValoracionResponse;
import ar.edu.iessf.servife.reputacion.repository.ValoracionRepository;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import ar.edu.iessf.servife.solicitudes.repository.SolicitudRepository;
import ar.edu.iessf.servife.solicitudes.service.ServicioDeSolicitudes;

/** Valoraciones (CU08): D1 (valorar una solicitud finalizada) y D2 (reseñas del perfil). */
@Service
public class ServicioDeValoraciones {

    private final ValoracionRepository valoraciones;
    private final SolicitudRepository solicitudes;
    private final ServicioDeSolicitudes servicioDeSolicitudes;
    private final ServicioDeFichaDePrestador fichas;

    public ServicioDeValoraciones(ValoracionRepository valoraciones, SolicitudRepository solicitudes,
            ServicioDeSolicitudes servicioDeSolicitudes, ServicioDeFichaDePrestador fichas) {
        this.valoraciones = valoraciones;
        this.solicitudes = solicitudes;
        this.servicioDeSolicitudes = servicioDeSolicitudes;
        this.fichas = fichas;
    }

    /**
     * D1. La porción 2 pasa la solicitud de FINALIZADA a VALORADA (D34): ajena es 404 y si no está
     * FINALIZADA, o ya se valoró, 409 TRANSICION_INVALIDA. Ese cambio condicional también corta la
     * carrera de dos valoraciones simultáneas. Después guarda la reseña y recalcula el promedio del
     * prestador, todo en la misma transacción.
     */
    @Transactional
    public ValoracionResponse valorar(UUID uuidSolicitud, ValoracionRequest pedido) {
        servicioDeSolicitudes.marcarComoValorada(uuidSolicitud);
        Solicitud solicitud = solicitudes.findDetalleByUuidAndEliminadoEnIsNull(uuidSolicitud)
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));

        Valoracion valoracion = valoraciones.saveAndFlush(new Valoracion(solicitud, solicitud.getCliente(),
            pedido.puntaje(), textoONulo(pedido.comentario())));
        valoraciones.recalcularPromedio(solicitud.getPrestador().getId());
        return aRespuesta(valoracion);
    }

    /** D2. Más recientes primero; solo de prestadores visibles (criterio de B6), si no 404. */
    @Transactional(readOnly = true)
    public Pagina<ValoracionResponse> listarDePrestador(UUID uuidPrestador, Integer page, Integer size) {
        Prestador prestador = fichas.visible(uuidPrestador);
        var pagina = Paginacion.pedir(page, size, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("uuid")));
        return Pagina.de(valoraciones.delPrestador(prestador, pagina), ServicioDeValoraciones::aRespuesta);
    }

    private static ValoracionResponse aRespuesta(Valoracion v) {
        return new ValoracionResponse(v.getUuid(), v.getPuntaje(), v.getComentario(),
            v.getCliente().getNombreApellido(), v.getCreadoEn());
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
