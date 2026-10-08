package ar.edu.iessf.servife.identidad.domain;

import java.time.LocalDate;

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

/** Cuenta de cliente (tabla clientes). */
@Entity
@Table(name = "clientes")
public class Cliente extends EntidadBase implements Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Long id;

    @Column(name = "nombre_apellido", nullable = false)
    private String nombreApellido;

    @Column(name = "fec_nacimiento")
    private LocalDate fecNacimiento;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "contrasenia", nullable = false)
    private String contrasenia;

    @Column(name = "telefono")
    private String telefono;

    @Column(name = "direccion")
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_cuenta", nullable = false)
    private EstadoCuenta estadoCuenta = EstadoCuenta.ACTIVA;

    @Override
    public Long getId() {
        return id;
    }

    protected Cliente() {
    }

    /** El email se guarda normalizado; {@code contraseniaHash} ya viene hasheada con BCrypt. */
    public Cliente(String nombreApellido, String email, String contraseniaHash) {
        this.nombreApellido = nombreApellido;
        this.email = Cuentas.normalizar(email);
        this.contrasenia = contraseniaHash;
    }

    @Override
    public Rol getRol() {
        return Rol.CLIENTE;
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

    public LocalDate getFecNacimiento() {
        return fecNacimiento;
    }

    public void setFecNacimiento(LocalDate fecNacimiento) {
        this.fecNacimiento = fecNacimiento;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    @Override
    public EstadoCuenta getEstadoCuenta() {
        return estadoCuenta;
    }

    public void setEstadoCuenta(EstadoCuenta estadoCuenta) {
        this.estadoCuenta = estadoCuenta;
    }
}
