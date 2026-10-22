package ar.edu.iessf.servife.gestion.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.iessf.servife.gestion.domain.Chat;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    /** El chat con su solicitud y las dos partes, para chequear quién es parte sin N+1. */
    @EntityGraph(attributePaths = {"solicitud", "solicitud.cliente", "solicitud.prestador"})
    Optional<Chat> findByUuidAndEliminadoEnIsNull(UUID uuid);

    @Query("select c.uuid from Chat c where c.solicitud.id = :idSolicitud and c.eliminadoEn is null")
    Optional<UUID> uuidDeLaSolicitud(@Param("idSolicitud") Long idSolicitud);
}
