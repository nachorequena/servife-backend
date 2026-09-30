package ar.edu.iessf.servife.reputacion.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.error.NoImplementadoException;

/**
 * Módulo D — Reputación y trabajos (dueño: Facundo Bustamante).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * Cada método es un stub que responde 501 hasta que se implemente: definí los DTOs en dto/,
 * la lógica en service/ y reemplazá el throw.
 */
@RestController
public class ValoracionController {

    /** D1 · POST /solicitudes/{uuid}/valoracion · CU08 · Solo si está FINALIZADA y una sola vez; si no, 409. Recalcula valoracion_promedio en la misma transacción. */
    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping("/solicitudes/{uuid}/valoracion")
    public ResponseEntity<Void> valorar(@PathVariable UUID uuid) {
        throw new NoImplementadoException("D1");
    }

    /** D2 · GET /prestadores/{uuid}/valoraciones · CU05, CU08 · Reseñas paginadas. El prestador no puede borrarlas ni ocultarlas (D07). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/prestadores/{uuid}/valoraciones")
    public ResponseEntity<Void> listarDePrestador(@PathVariable UUID uuid) {
        throw new NoImplementadoException("D2");
    }
}
