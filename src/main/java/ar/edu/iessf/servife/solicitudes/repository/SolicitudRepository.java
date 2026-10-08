package ar.edu.iessf.servife.solicitudes.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {

    Optional<Solicitud> findByUuidAndEliminadoEnIsNull(UUID uuid);

    /** Detalle con las partes y el tipo ya cargados (C3). */
    @EntityGraph(attributePaths = {"cliente", "prestador", "tipoServicio"})
    Optional<Solicitud> findDetalleByUuidAndEliminadoEnIsNull(UUID uuid);

    /** C2 para el cliente: una sola consulta con join a las partes y al tipo; el conteo va aparte. */
    @EntityGraph(attributePaths = {"cliente", "prestador", "tipoServicio"})
    Page<Solicitud> findByClienteAndEstadoInAndEliminadoEnIsNull(Cliente cliente, Collection<EstadoSolicitud> estados,
            Pageable pageable);

    /** C2 para el prestador. */
    @EntityGraph(attributePaths = {"cliente", "prestador", "tipoServicio"})
    Page<Solicitud> findByPrestadorAndEstadoInAndEliminadoEnIsNull(Prestador prestador,
            Collection<EstadoSolicitud> estados, Pageable pageable);

    /** uuid de las imágenes adjuntas a la solicitud, en el orden en que se subieron. */
    @Query(value = "SELECT a.uuid FROM solicitud_imagenes si JOIN archivos a ON a.id_archivo = si.id_archivo "
        + "WHERE si.id_solicitud = :idSolicitud ORDER BY a.id_archivo", nativeQuery = true)
    List<UUID> uuidsDeImagenes(@Param("idSolicitud") Long idSolicitud);

    /** Adjunta una imagen a la solicitud (tabla solicitud_imagenes). */
    @Modifying
    @Query(value = "INSERT INTO solicitud_imagenes (id_solicitud, id_archivo) VALUES (:idSolicitud, :idArchivo)", nativeQuery = true)
    void vincularImagen(@Param("idSolicitud") Long idSolicitud, @Param("idArchivo") Long idArchivo);

    /** Si la imagen ya está adjunta a alguna solicitud. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM solicitud_imagenes WHERE id_archivo = :idArchivo)", nativeQuery = true)
    boolean imagenEnUso(@Param("idArchivo") Long idArchivo);

    /**
     * Cambio de estado atómico (C4): solo si la solicitud sigue en el estado de origen. Devuelve las filas
     * tocadas (0 = otro la cambió antes). motivo, canceladaPor y precio nulos conservan lo que había.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Solicitud s set s.estado = :destino, s.motivo = coalesce(:motivo, s.motivo), "
        + "s.canceladaPor = coalesce(:canceladaPor, s.canceladaPor), "
        + "s.precioAcordado = coalesce(:precio, s.precioAcordado), s.actualizadoEn = :ahora "
        + "where s.id = :id and s.estado = :origen and s.eliminadoEn is null")
    int cambiarEstado(@Param("id") Long id, @Param("origen") EstadoSolicitud origen,
            @Param("destino") EstadoSolicitud destino, @Param("motivo") String motivo,
            @Param("canceladaPor") Rol canceladaPor, @Param("precio") Long precio, @Param("ahora") Instant ahora);
}
