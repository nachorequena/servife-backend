package ar.edu.iessf.servife.reputacion.domain;

import java.time.LocalDate;

import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Trabajo realizado que publica un prestador (tabla publicaciones, CU10). Puede colgar de una solicitud
 * terminada propia: entonces la tarjeta muestra la valoración de esa solicitud (D07, opción A). Las
 * imágenes van en publicacion_imagenes. id_gestor no se mapea: no hay caso de uso que lo use.
 */
@Entity
@Table(name = "publicaciones")
public class Publicacion extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_publicacion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_prestador", nullable = false, updatable = false)
    private Prestador prestador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_solicitud")
    private Solicitud solicitud;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha_trabajo")
    private LocalDate fechaTrabajo;

    protected Publicacion() {
    }

    public Publicacion(Prestador prestador, Solicitud solicitud, String titulo, String descripcion,
            LocalDate fechaTrabajo) {
        this.prestador = prestador;
        actualizar(solicitud, titulo, descripcion, fechaTrabajo);
    }

    /** D5: reemplaza todo lo editable; los valores ya vienen normalizados por el servicio. */
    public void actualizar(Solicitud solicitud, String titulo, String descripcion, LocalDate fechaTrabajo) {
        this.solicitud = solicitud;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.fechaTrabajo = fechaTrabajo;
    }

    public Long getId() {
        return id;
    }

    public Prestador getPrestador() {
        return prestador;
    }

    public Solicitud getSolicitud() {
        return solicitud;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public LocalDate getFechaTrabajo() {
        return fechaTrabajo;
    }
}
