package ar.edu.iessf.servife.solicitudes.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.iessf.servife.solicitudes.domain.Solicitud;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {

    Optional<Solicitud> findByUuidAndEliminadoEnIsNull(UUID uuid);
}
