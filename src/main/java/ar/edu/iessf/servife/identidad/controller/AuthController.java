package ar.edu.iessf.servife.identidad.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.error.NoImplementadoException;
import ar.edu.iessf.servife.identidad.dto.LoginRequest;
import ar.edu.iessf.servife.identidad.dto.RefreshRequest;
import ar.edu.iessf.servife.identidad.dto.RegistroRequest;
import ar.edu.iessf.servife.identidad.dto.TokensResponse;
import ar.edu.iessf.servife.identidad.dto.UsuarioResponse;
import ar.edu.iessf.servife.identidad.service.ServicioDeRegistro;
import ar.edu.iessf.servife.identidad.service.ServicioDeSesion;
import jakarta.validation.Valid;

/**
 * Módulo A — Identidad y cuentas (dueño: Pedro Soria).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * A1 (registro), A2 (login) y A3 (refresh) están implementados; los demás endpoints son stubs que
 * responden 501 hasta que se
 * implementen: definí los DTOs en dto/, la lógica en service/ y reemplazá el throw.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final ServicioDeRegistro registro;

    private final ServicioDeSesion sesion;

    public AuthController(ServicioDeRegistro registro, ServicioDeSesion sesion) {
        this.registro = registro;
        this.sesion = sesion;
    }

    /** A1 · POST /auth/registro · CU01 · Solo CLIENTE o PRESTADOR. Si es prestador recibe idTipoServicio y crea todo en una transacción; queda PENDIENTE. */
    @PreAuthorize("permitAll()")
    @PostMapping("/registro")
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroRequest pedido) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registro.registrar(pedido));
    }

    /** A2 · POST /auth/login · CU02 · Único login para los tres roles. Devuelve access + refresh y el rol. */
    @PreAuthorize("permitAll()")
    @PostMapping("/login")
    public ResponseEntity<TokensResponse> iniciarSesion(@Valid @RequestBody LoginRequest pedido) {
        return ResponseEntity.ok(sesion.iniciar(pedido));
    }

    /** A3 · POST /auth/refresh · CU02 · Renueva el access token con el refresh token. */
    @PreAuthorize("permitAll()")
    @PostMapping("/refresh")
    public ResponseEntity<TokensResponse> renovarToken(@Valid @RequestBody RefreshRequest pedido) {
        return ResponseEntity.ok(sesion.renovar(pedido));
    }

    /** A4 · GET /auth/me · CU02 · Datos del usuario autenticado. La app lo llama al abrir. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<Void> obtenerSesion() {
        throw new NoImplementadoException("A4");
    }

    /** A8 · POST /auth/recuperar · CU02 · Envía el correo de recuperación. */
    @PreAuthorize("permitAll()")
    @PostMapping("/recuperar")
    public ResponseEntity<Void> recuperarContrasenia() {
        throw new NoImplementadoException("A8");
    }
}
