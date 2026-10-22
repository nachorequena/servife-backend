package ar.edu.iessf.servife.gestion.domain;

import java.util.UUID;

import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Documento del expediente de validación de un prestador (tabla documentos_validacion, D01). Los bytes
 * viven fuera de la base, en AlmacenDeDocumentos, con ruta como nombre (un uuid propio, distinto del
 * uuid público). Datos personales (Ley 25.326): nunca salen por /archivos y se borran al resolver.
 */
@Entity
@Table(name = "documentos_validacion")
public class DocumentoValidacion extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento")
    private Long id;

    @Column(name = "id_prestador", nullable = false, updatable = false)
    private Long idPrestador;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false)
    private TipoDeDocumento tipo;

    @Column(name = "ruta", nullable = false, updatable = false)
    private String ruta;

    @Column(name = "mime", nullable = false, updatable = false)
    private String mime;

    @Column(name = "bytes", nullable = false, updatable = false)
    private long bytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoValidacion estado = EstadoValidacion.PENDIENTE;

    protected DocumentoValidacion() {
    }

    public DocumentoValidacion(Long idPrestador, TipoDeDocumento tipo, String mime, long bytes) {
        this.idPrestador = idPrestador;
        this.tipo = tipo;
        this.mime = mime;
        this.bytes = bytes;
        this.ruta = UUID.randomUUID().toString();
    }

    public Long getId() {
        return id;
    }

    public Long getIdPrestador() {
        return idPrestador;
    }

    public TipoDeDocumento getTipo() {
        return tipo;
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

    public EstadoValidacion getEstado() {
        return estado;
    }

    public void setEstado(EstadoValidacion estado) {
        this.estado = estado;
    }
}
