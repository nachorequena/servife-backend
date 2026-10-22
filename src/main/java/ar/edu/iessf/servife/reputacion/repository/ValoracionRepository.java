package ar.edu.iessf.servife.reputacion.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.reputacion.domain.Valoracion;

public interface ValoracionRepository extends JpaRepository<Valoracion, Long> {

    /** Puntaje de la valoración de una solicitud. */
    interface PuntajeDeSolicitud {
        Long getIdSolicitud();

        int getPuntaje();
    }

    /** D2: las reseñas del prestador (el prestador sale de la solicitud), con el cliente ya cargado. */
    @Query(value = "select v from Valoracion v join fetch v.cliente join v.solicitud s "
        + "where s.prestador = :prestador and v.eliminadoEn is null",
        countQuery = "select count(v) from Valoracion v join v.solicitud s "
            + "where s.prestador = :prestador and v.eliminadoEn is null")
    Page<Valoracion> delPrestador(@Param("prestador") Prestador prestador, Pageable pagina);

    /** Puntajes de varias solicitudes en una sola consulta (las estrellas de las tarjetas de D3 y D8). */
    @Query("select v.solicitud.id as idSolicitud, v.puntaje as puntaje from Valoracion v "
        + "where v.solicitud.id in :ids and v.eliminadoEn is null")
    List<PuntajeDeSolicitud> puntajesDe(@Param("ids") Collection<Long> idsDeSolicitudes);
}
