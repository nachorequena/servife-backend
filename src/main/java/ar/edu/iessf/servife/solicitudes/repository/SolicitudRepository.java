package ar.edu.iessf.servife.solicitudes.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.iessf.servife.solicitudes.domain.Solicitud;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {

    Optional<Solicitud> findByUuidAndEliminadoEnIsNull(UUID uuid);

    /** Adjunta una imagen a la solicitud (tabla solicitud_imagenes). */
    @Modifying
    @Query(value = "INSERT INTO solicitud_imagenes (id_solicitud, id_archivo) VALUES (:idSolicitud, :idArchivo)", nativeQuery = true)
    void vincularImagen(@Param("idSolicitud") Long idSolicitud, @Param("idArchivo") Long idArchivo);

    /** Si la imagen ya está adjunta a alguna solicitud. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM solicitud_imagenes WHERE id_archivo = :idArchivo)", nativeQuery = true)
    boolean imagenEnUso(@Param("idArchivo") Long idArchivo);
}
