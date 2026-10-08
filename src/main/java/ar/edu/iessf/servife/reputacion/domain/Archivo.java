package ar.edu.iessf.servife.reputacion.domain;

import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import ar.edu.iessf.servife.common.seguridad.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Imagen subida (tabla archivos). Los bytes viven en el almacén, con el uuid como nombre; la ruta
 * guarda ese nombre, nunca un camino del cliente. El dueño es polimórfico: id + rol, sin FK.
 */
@Entity
@Table(name = "archivos")
public class Archivo extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_archivo")
    private Long id;

    @Column(name = "ruta", nullable = false)
    private String ruta;

    @Column(name = "mime", nullable = false)
    private String mime;

    @Column(name = "bytes", nullable = false)
    private long bytes;

    @Column(name = "id_propietario", nullable = false, updatable = false)
    private Long idPropietario;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_propietario", nullable = false, updatable = false)
    private Rol rolPropietario;

    protected Archivo() {
    }

    public Archivo(String mime, long bytes, Long idPropietario, Rol rolPropietario) {
        this.mime = mime;
        this.bytes = bytes;
        this.idPropietario = idPropietario;
        this.rolPropietario = rolPropietario;
    }

    /** El nombre en disco es el uuid; se fija al persistir, después de que EntidadBase lo genera. */
    @PrePersist
    void fijarRuta() {
        this.ruta = getUuid().toString();
    }

    public Long getId() {
        return id;
    }

    public String getRuta() {
        return ruta;
    }

    public String getMime() {
        return mime;
    }

    public long getBytes() {
        return bytes;
    }

    public Long getIdPropietario() {
        return idPropietario;
    }

    public Rol getRolPropietario() {
        return rolPropietario;
    }
}
