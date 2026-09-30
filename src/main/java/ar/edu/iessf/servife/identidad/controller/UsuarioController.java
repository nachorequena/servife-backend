package ar.edu.iessf.servife.identidad.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.error.NoImplementadoException;

/**
 * Módulo A — Identidad y cuentas (dueño: Pedro Soria).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * Cada método es un stub que responde 501 hasta que se implemente: definí los DTOs en dto/,
 * la lógica en service/ y reemplazá el throw.
 */
@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    /** A6 · PUT /usuarios/me · CU03 · Edita los datos propios. */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me")
    public ResponseEntity<Void> actualizarMiUsuario() {
        throw new NoImplementadoException("A6");
    }

    /** A7 · PATCH /usuarios/me/password · CU03 · Cambio de contraseña verificando la anterior. */
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/me/password")
    public ResponseEntity<Void> cambiarContrasenia() {
        throw new NoImplementadoException("A7");
    }
}
