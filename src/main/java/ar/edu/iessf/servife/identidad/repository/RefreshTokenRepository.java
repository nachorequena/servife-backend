package ar.edu.iessf.servife.identidad.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ar.edu.iessf.servife.identidad.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Recibe el SHA-256 hex del token, nunca el token. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoca el token solo si sigue vigente (no revocado ni vencido). Es una sola sentencia
     * atómica: si dos pedidos usan el mismo refresh a la vez, solo uno recibe 1.
     */
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revocadoEn = :ahora "
        + "where t.tokenHash = :hash and t.revocadoEn is null and t.expiraEn > :ahora")
    int revocarSiVigente(@Param("hash") String hash, @Param("ahora") Instant ahora);

    /** Revoca todos los tokens vigentes de un usuario (cambio y recuperación de contraseña). */
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revocadoEn = :ahora where t.uuidUsuario = :uuid and t.revocadoEn is null")
    int revocarTodosDe(@Param("uuid") UUID uuid, @Param("ahora") Instant ahora);
}
