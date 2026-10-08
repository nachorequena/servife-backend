package ar.edu.iessf.servife.gestion.domain;

import java.util.UUID;

import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Aviso dentro de la app (tabla notificaciones). El destinatario se guarda como id interno +
 * rol porque las cuentas viven en tres tablas; esos datos nunca salen hacia la app.
 */
@Entity
@Table(name = "notificaciones")
public class Aviso extends EntidadBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Long id;

    @Column(name = "id_usuario", nullable = false, updatable = false)
    private Long idUsuario;

    @Column(name = "rol_usuario", nullable = false, updatable = false)
    private String rolUsuario;

    @Column(name = "tipo", nullable = false, updatable = false)
    private String tipo;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "cuerpo")
    private String cuerpo;

    @Column(name = "uuid_solicitud", updatable = false)
    private UUID uuidSolicitud;

    @Column(name = "leida", nullable = false)
    private boolean leida;

    protected Aviso() {
    }

    public Aviso(Long idUsuario, String rolUsuario, String tipo, String titulo, String cuerpo, UUID uuidSolicitud) {
        this.idUsuario = idUsuario;
        this.rolUsuario = rolUsuario;
        this.tipo = tipo;
        this.titulo = titulo;
        this.cuerpo = cuerpo;
        this.uuidSolicitud = uuidSolicitud;
    }

    public void marcarLeido() {
        this.leida = true;
    }

    public String getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getCuerpo() {
        return cuerpo;
    }

    public UUID getUuidSolicitud() {
        return uuidSolicitud;
    }

    public boolean isLeida() {
        return leida;
    }
}
