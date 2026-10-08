package ar.edu.iessf.servife.reputacion.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.iessf.servife.reputacion.domain.Archivo;

public interface ArchivoRepository extends JpaRepository<Archivo, Long> {

    Optional<Archivo> findByUuidAndEliminadoEnIsNull(UUID uuid);

    /** Si la cuenta es el cliente o el prestador de alguna solicitud no eliminada que incluye el archivo. */
    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM solicitud_imagenes si
            JOIN solicitudes_servicio s ON s.id_solicitud = si.id_solicitud
            WHERE si.id_archivo = :idArchivo AND s.eliminado_en IS NULL
              AND ((:rol = 'CLIENTE' AND s.id_cliente = :idCuenta)
                OR (:rol = 'PRESTADOR' AND s.id_prestador = :idCuenta)))
        """, nativeQuery = true)
    boolean esParticipanteDeSolicitudConArchivo(@Param("idArchivo") Long idArchivo,
        @Param("idCuenta") Long idCuenta, @Param("rol") String rol);
}
