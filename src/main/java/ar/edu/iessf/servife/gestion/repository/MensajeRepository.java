package ar.edu.iessf.servife.gestion.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.gestion.domain.Chat;
import ar.edu.iessf.servife.gestion.domain.Mensaje;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    /** Todo el chat, en el orden en que se escribió. */
    List<Mensaje> findByChatAndEliminadoEnIsNullOrderByIdAsc(Chat chat);

    /** Los mensajes posteriores a uno dado (polling con ?desde=). */
    List<Mensaje> findByChatAndIdGreaterThanAndEliminadoEnIsNullOrderByIdAsc(Chat chat, Long id);

    Optional<Mensaje> findByUuidAndChatAndEliminadoEnIsNull(UUID uuid, Chat chat);

    /** Marca como leídos los mensajes que escribió la otra parte. Se llama antes de cargar los mensajes. */
    @Modifying(flushAutomatically = true)
    @Query("update Mensaje m set m.leido = true where m.chat = :chat and m.rolEmisor <> :lector and m.leido = false")
    int marcarLeidos(@Param("chat") Chat chat, @Param("lector") Rol lector);
}
