package ar.edu.iessf.servife.identidad.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.identidad.dto.ConfirmarRecuperacionRequest;
import ar.edu.iessf.servife.identidad.dto.LoginRequest;
import ar.edu.iessf.servife.identidad.dto.RecuperarRequest;
import ar.edu.iessf.servife.identidad.dto.RefreshRequest;
import ar.edu.iessf.servife.identidad.dto.RegistroRequest;
import ar.edu.iessf.servife.identidad.dto.TokensResponse;
import ar.edu.iessf.servife.identidad.dto.UsuarioResponse;
import ar.edu.iessf.servife.identidad.service.ServicioDeCuenta;
import ar.edu.iessf.servife.identidad.service.ServicioDeRecuperacion;
import ar.edu.iessf.servife.identidad.service.ServicioDeRegistro;
import ar.edu.iessf.servife.identidad.service.ServicioDeSesion;
import jakarta.validation.Valid;

/**
 * Módulo A — Identidad y cuentas (dueño: Pedro Soria).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * Implementados: A1 (registro), A2 (login), A3 (refresh), A4 (sesión), A8 (recuperar) y A9 (confirmar recuperación).
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final ServicioDeRegistro registro;

    private final ServicioDeSesion sesion;

    private final ServicioDeCuenta cuenta;

    private final ServicioDeRecuperacion recuperacion;

    private final UsuarioActual usuarioActual;

    public AuthController(ServicioDeRegistro registro, ServicioDeSesion sesion, ServicioDeCuenta cuenta,
        ServicioDeRecuperacion recuperacion, UsuarioActual usuarioActual) {
        this.registro = registro;
        this.sesion = sesion;
        this.cuenta = cuenta;
        this.recuperacion = recuperacion;
        this.usuarioActual = usuarioActual;
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
    public ResponseEntity<UsuarioResponse> obtenerSesion() {
        return ResponseEntity.ok(cuenta.obtener(usuarioActual.uuid(), usuarioActual.rol()));
    }

    /** A8 · POST /auth/recuperar · CU02 · Envía por correo un código de 6 dígitos. Siempre 204, exista o no la cuenta. */
    @PreAuthorize("permitAll()")
    @PostMapping("/recuperar")
    public ResponseEntity<Void> recuperarContrasenia(@Valid @RequestBody RecuperarRequest pedido) {
        recuperacion.solicitar(pedido);
        return ResponseEntity.noContent().build();
    }

    /** A9 · POST /auth/recuperar/confirmar · CU02 · Cambia la contraseña con el código recibido. 204 o 400 CODIGO_INVALIDO. */
    @PreAuthorize("permitAll()")
    @PostMapping("/recuperar/confirmar")
    public ResponseEntity<Void> confirmarRecuperacion(@Valid @RequestBody ConfirmarRecuperacionRequest pedido) {
        recuperacion.confirmar(pedido);
        return ResponseEntity.noContent().build();
    }
}
