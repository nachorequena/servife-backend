package ar.edu.iessf.servife.reputacion.domain;

import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import ar.edu.iessf.servife.identidad.domain.Cliente;
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
 * Reseña del cliente sobre una solicitud terminada (tabla valoraciones, CU08): de 1 a 5 y una sola por
 * solicitud (id_solicitud UNIQUE). No tiene id_prestador: el prestador sale de la solicitud. El
 * prestador no puede borrarlas ni ocultarlas (D07).
 */
@Entity
@Table(name = "valoraciones")
public class Valoracion extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_valoracion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_solicitud", nullable = false, updatable = false)
    private Solicitud solicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false, updatable = false)
    private Cliente cliente;

    @Column(name = "puntaje", nullable = false, updatable = false)
    private int puntaje;

    @Column(name = "comentario", columnDefinition = "TEXT", updatable = false)
    private String comentario;

    protected Valoracion() {
    }

    public Valoracion(Solicitud solicitud, Cliente cliente, int puntaje, String comentario) {
        this.solicitud = solicitud;
        this.cliente = cliente;
        this.puntaje = puntaje;
        this.comentario = comentario;
    }

    public Long getId() {
        return id;
    }

    public Solicitud getSolicitud() {
        return solicitud;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public int getPuntaje() {
        return puntaje;
    }

    public String getComentario() {
        return comentario;
    }
}
