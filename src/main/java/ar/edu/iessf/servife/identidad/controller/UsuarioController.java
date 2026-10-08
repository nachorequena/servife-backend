package ar.edu.iessf.servife.identidad.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.identidad.dto.ActualizarUsuarioRequest;
import ar.edu.iessf.servife.identidad.dto.CambiarContraseniaRequest;
import ar.edu.iessf.servife.identidad.dto.UsuarioResponse;
import ar.edu.iessf.servife.identidad.service.Contrasenias;
import ar.edu.iessf.servife.identidad.service.ServicioDeCuenta;
import jakarta.validation.Valid;

/**
 * Módulo A — Identidad y cuentas (dueño: Pedro Soria).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * A6 (datos de la cuenta) y A7 (cambio de contraseña) están implementados.
 */
@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final ServicioDeCuenta cuenta;

    private final UsuarioActual usuarioActual;

    public UsuarioController(ServicioDeCuenta cuenta, UsuarioActual usuarioActual) {
        this.cuenta = cuenta;
        this.usuarioActual = usuarioActual;
    }

    /** A6 · PUT /usuarios/me · CU03 · Edita los datos propios. */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me")
    public ResponseEntity<UsuarioResponse> actualizarMiUsuario(@Valid @RequestBody ActualizarUsuarioRequest pedido) {
        return ResponseEntity.ok(cuenta.actualizar(usuarioActual.uuid(), usuarioActual.rol(), pedido));
    }

    /** A7 · PATCH /usuarios/me/password · CU03 · Cambio de contraseña verificando la anterior. */
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/me/password")
    public ResponseEntity<Void> cambiarContrasenia(@Valid @RequestBody CambiarContraseniaRequest pedido) {
        // BCrypt falla con más de 72 bytes: se corta acá con 400 antes de codificar.
        Contrasenias.validarLargo("contraseniaNueva", pedido.contraseniaNueva());
        cuenta.cambiarContrasenia(usuarioActual.uuid(), usuarioActual.rol(), pedido);
        return ResponseEntity.noContent().build();
    }
}
