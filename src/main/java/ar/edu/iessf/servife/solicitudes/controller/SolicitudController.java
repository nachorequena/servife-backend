package ar.edu.iessf.servife.solicitudes.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import ar.edu.iessf.servife.common.error.NoImplementadoException;
import ar.edu.iessf.servife.solicitudes.dto.CrearSolicitudRequest;
import ar.edu.iessf.servife.solicitudes.dto.SolicitudResponse;
import ar.edu.iessf.servife.solicitudes.service.ServicioDeSolicitudes;
import jakarta.validation.Valid;

/**
 * Módulo C — Solicitudes (dueño: Tomás Ferreyra).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * C1 está implementado; los demás métodos son stubs que responden 501 hasta que se implementen.
 */
@RestController
@RequestMapping("/solicitudes")
public class SolicitudController {

    private final ServicioDeSolicitudes servicio;

    public SolicitudController(ServicioDeSolicitudes servicio) {
        this.servicio = servicio;
    }

    /** C1 · POST /solicitudes · CU06 · Fecha deseada, hora preferida, dirección, descripción e imagenIds ya subidas (D7). Queda PENDIENTE y habilita el chat (D03). 201 con Location. */
    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping
    public ResponseEntity<SolicitudResponse> crear(@Valid @RequestBody CrearSolicitudRequest pedido) {
        SolicitudResponse creada = servicio.crear(pedido);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
            .buildAndExpand(creada.uuid()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    /** C2 · GET /solicitudes · CU06, CU09 · Las del usuario autenticado, filtrable por estado. Paginado. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<Void> listarMias() {
        throw new NoImplementadoException("C2");
    }

    /** C3 · GET /solicitudes/{uuid} · CU06, CU09 · 404 si no sos parte de la solicitud. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{uuid}")
    public ResponseEntity<Void> obtener(@PathVariable UUID uuid) {
        throw new NoImplementadoException("C3");
    }

    /** C4 · PATCH /solicitudes/{uuid}/estado · CU09 · Única puerta de cambio de estado (.ai/02-context.md §Máquina de estados). Salto inválido: 409 TRANSICION_INVALIDA. Notifica a la contraparte. */
    @PreAuthorize("hasAnyRole('CLIENTE', 'PRESTADOR')")
    @PatchMapping("/{uuid}/estado")
    public ResponseEntity<Void> cambiarEstado(@PathVariable UUID uuid) {
        throw new NoImplementadoException("C4");
    }
}
