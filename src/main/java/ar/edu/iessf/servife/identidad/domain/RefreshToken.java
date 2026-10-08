package ar.edu.iessf.servife.identidad.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import ar.edu.iessf.servife.common.seguridad.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Refresh token opaco (tabla refresh_tokens). Solo se guarda el SHA-256 del token. No extiende
 * EntidadBase: la tabla no tiene uuid, actualizado_en ni eliminado_en.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_refresh_token")
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "uuid_usuario", nullable = false, updatable = false)
    private UUID uuidUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_usuario", nullable = false, updatable = false)
    private Rol rol;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected RefreshToken() {
    }

    public RefreshToken(String tokenHash, UUID uuidUsuario, Rol rol, Instant creadoEn, Instant expiraEn) {
        this.tokenHash = tokenHash;
        this.uuidUsuario = uuidUsuario;
        this.rol = rol;
        this.creadoEn = creadoEn;
        this.expiraEn = expiraEn;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getUuidUsuario() {
        return uuidUsuario;
    }

    public Rol getRol() {
        return rol;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getRevocadoEn() {
        return revocadoEn;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
