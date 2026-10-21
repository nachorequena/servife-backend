package ar.edu.iessf.servife.gestion.domain;

import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import ar.edu.iessf.servife.common.seguridad.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Mensaje de texto de un chat (tabla mensajes). El emisor es id + rol, sin FK, porque las cuentas viven en
 * tres tablas; esos datos nunca salen hacia la app (la app recibe "propio").
 */
@Entity
@Table(name = "mensajes")
public class Mensaje extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mensaje")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_chat", nullable = false, updatable = false)
    private Chat chat;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_emisor", nullable = false, updatable = false)
    private Rol rolEmisor;

    @Column(name = "id_emisor", nullable = false, updatable = false)
    private Long idEmisor;

    @Column(name = "texto", nullable = false, updatable = false)
    private String texto;

    @Column(name = "leido", nullable = false)
    private boolean leido;

    protected Mensaje() {
    }

    public Mensaje(Chat chat, Rol rolEmisor, Long idEmisor, String texto) {
        this.chat = chat;
        this.rolEmisor = rolEmisor;
        this.idEmisor = idEmisor;
        this.texto = texto;
    }

    public Long getId() {
        return id;
    }

    public Rol getRolEmisor() {
        return rolEmisor;
    }

    public String getTexto() {
        return texto;
    }

    public boolean isLeido() {
        return leido;
    }
}
