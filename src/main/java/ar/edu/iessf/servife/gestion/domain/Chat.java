package ar.edu.iessf.servife.gestion.domain;

import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Chat de una solicitud (tabla chats, D33): uno por solicitud, creado junto con ella en C1. */
@Entity
@Table(name = "chats")
public class Chat extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_chat")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_solicitud", nullable = false, updatable = false, unique = true)
    private Solicitud solicitud;

    protected Chat() {
    }

    public Chat(Solicitud solicitud) {
        this.solicitud = solicitud;
    }

    public Long getId() {
        return id;
    }

    public Solicitud getSolicitud() {
        return solicitud;
    }
}
