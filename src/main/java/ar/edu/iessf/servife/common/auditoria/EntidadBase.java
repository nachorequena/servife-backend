package ar.edu.iessf.servife.common.auditoria;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * Columnas comunes a todas las tablas (.ai/04-database-schema.md): uuid, creado_en,
 * actualizado_en y eliminado_en (baja lógica).
 * La PK BIGINT la declara cada entidad con su nombre (id_cliente, id_solicitud...) y nunca sale
 * del backend: hacia la app solo viaja el uuid.
 */
@MappedSuperclass
public abstract class EntidadBase {

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "eliminado_en")
    private Instant eliminadoEn;

    @PrePersist
    protected void alCrear() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        creadoEn = Instant.now();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    protected void alActualizar() {
        actualizadoEn = Instant.now();
    }

    public void darDeBaja() {
        eliminadoEn = Instant.now();
    }

    public boolean estaEliminado() {
        return eliminadoEn != null;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public Instant getEliminadoEn() {
        return eliminadoEn;
    }
}
