package ar.edu.iessf.servife.identidad.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.iessf.servife.identidad.domain.CodigoRecuperacion;

public interface CodigoRecuperacionRepository extends JpaRepository<CodigoRecuperacion, Long> {

    /** El último código sin usar del email (que debe venir normalizado). */
    Optional<CodigoRecuperacion> findFirstByEmailAndUsadoEnIsNullOrderByIdDesc(String email);

    /** Marca como usados todos los códigos pendientes del email: pedir uno nuevo invalida los anteriores. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CodigoRecuperacion c set c.usadoEn = :ahora where c.email = :email and c.usadoEn is null")
    int invalidarPendientes(@Param("email") String email, @Param("ahora") Instant ahora);

    /**
     * Suma un intento fallido solo si el código sigue vigente (sin usar, con menos de 5 intentos y sin
     * vencer). Es una sola sentencia atómica: pedidos concurrentes no pueden pasar el tope de 5.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CodigoRecuperacion c set c.intentos = c.intentos + 1 "
        + "where c.id = :id and c.usadoEn is null and c.intentos < 5 and c.expiraEn > :ahora")
    int sumarIntentoSiVigente(@Param("id") Long id, @Param("ahora") Instant ahora);

    /** Marca el código como usado solo si sigue vigente; si dos pedidos lo usan a la vez, solo uno recibe 1. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CodigoRecuperacion c set c.usadoEn = :ahora "
        + "where c.id = :id and c.usadoEn is null and c.intentos < 5 and c.expiraEn > :ahora")
    int usarSiVigente(@Param("id") Long id, @Param("ahora") Instant ahora);
}
