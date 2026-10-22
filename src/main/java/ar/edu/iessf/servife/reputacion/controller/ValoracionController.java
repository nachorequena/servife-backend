package ar.edu.iessf.servife.reputacion.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.reputacion.dto.ValoracionRequest;
import ar.edu.iessf.servife.reputacion.dto.ValoracionResponse;
import ar.edu.iessf.servife.reputacion.service.ServicioDeValoraciones;
import jakarta.validation.Valid;

/**
 * Módulo D — Reputación y trabajos (dueño: Facundo Bustamante).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 */
@RestController
public class ValoracionController {

    private final ServicioDeValoraciones servicio;

    public ValoracionController(ServicioDeValoraciones servicio) {
        this.servicio = servicio;
    }

    /** D1 · POST /solicitudes/{uuid}/valoracion · CU08 · Solo si está FINALIZADA y una sola vez; si no, 409. Recalcula valoracion_promedio en la misma transacción. 201 sin Location: la reseña se lee por D2. */
    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping("/solicitudes/{uuid}/valoracion")
    public ResponseEntity<ValoracionResponse> valorar(@PathVariable UUID uuid, @Valid @RequestBody ValoracionRequest pedido) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.valorar(uuid, pedido));
    }

    /** D2 · GET /prestadores/{uuid}/valoraciones · CU05, CU08 · Reseñas paginadas, más recientes primero. El prestador no puede borrarlas ni ocultarlas (D07). 404 si el prestador no está visible (criterio de B6). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/prestadores/{uuid}/valoraciones")
    public Pagina<ValoracionResponse> listarDePrestador(@PathVariable UUID uuid,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return servicio.listarDePrestador(uuid, page, size);
    }
}
