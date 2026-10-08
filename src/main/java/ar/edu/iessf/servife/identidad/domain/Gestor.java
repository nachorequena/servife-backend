package ar.edu.iessf.servife.identidad.domain;

import ar.edu.iessf.servife.common.auditoria.EntidadBase;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.service.Cuentas;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Cuenta de gestor (tabla gestores). Nunca se crea por el registro público. */
@Entity
@Table(name = "gestores")
public class Gestor extends EntidadBase implements Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_gestor")
    private Long id;

    @Column(name = "nombre_apellido", nullable = false)
    private String nombreApellido;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "contrasenia", nullable = false)
    private String contrasenia;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_cuenta", nullable = false)
    private EstadoCuenta estadoCuenta = EstadoCuenta.ACTIVA;

    protected Gestor() {
    }

    /** El email se guarda normalizado; {@code contraseniaHash} ya viene hasheada con BCrypt. */
    public Gestor(String nombreApellido, String email, String contraseniaHash) {
        this.nombreApellido = nombreApellido;
        this.email = Cuentas.normalizar(email);
        this.contrasenia = contraseniaHash;
    }

    @Override
    public Rol getRol() {
        return Rol.GESTOR;
    }

    @Override
    public void cambiarContrasenia(String hash) {
        this.contrasenia = hash;
    }

    @Override
    public String getNombreApellido() {
        return nombreApellido;
    }

    public void setNombreApellido(String nombreApellido) {
        this.nombreApellido = nombreApellido;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getContrasenia() {
        return contrasenia;
    }

    @Override
    public EstadoCuenta getEstadoCuenta() {
        return estadoCuenta;
    }

    public void setEstadoCuenta(EstadoCuenta estadoCuenta) {
        this.estadoCuenta = estadoCuenta;
    }
}
