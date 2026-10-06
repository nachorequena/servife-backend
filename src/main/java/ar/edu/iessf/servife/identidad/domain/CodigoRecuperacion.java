package ar.edu.iessf.servife.identidad.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Código de recuperación de contraseña (tabla codigos_recuperacion). Solo se guarda el SHA-256 del
 * código de 6 dígitos. No extiende EntidadBase: la tabla no tiene uuid ni eliminado_en.
 */
@Entity
@Table(name = "codigos_recuperacion")
public class CodigoRecuperacion {

    /** Con este número de intentos fallidos el código deja de servir. */
    public static final int MAX_INTENTOS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_codigo")
    private Long id;

    @Column(name = "email", nullable = false, updatable = false, length = 254)
    private String email;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "codigo_hash", nullable = false, updatable = false, length = 64)
    private String codigoHash;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected CodigoRecuperacion() {
    }

    public CodigoRecuperacion(String email, String codigoHash, Instant creadoEn, Instant expiraEn) {
        this.email = email;
        this.codigoHash = codigoHash;
        this.creadoEn = creadoEn;
        this.expiraEn = expiraEn;
    }

    /** Sirve si no fue usado, no venció y no agotó los intentos. */
    public boolean estaVigente(Instant ahora) {
        return usadoEn == null && intentos < MAX_INTENTOS && ahora.isBefore(expiraEn);
    }

    public void registrarIntentoFallido() {
        intentos++;
    }

    public void marcarUsado(Instant ahora) {
        usadoEn = ahora;
    }

    public String getEmail() {
        return email;
    }

    public String getCodigoHash() {
        return codigoHash;
    }

    public int getIntentos() {
        return intentos;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getUsadoEn() {
        return usadoEn;
    }
}
