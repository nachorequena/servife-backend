package ar.edu.iessf.servife.reputacion.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.error.NoImplementadoException;

/**
 * Módulo D — Reputación y trabajos (dueño: Facundo Bustamante).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * Cada método es un stub que responde 501 hasta que se implemente: definí los DTOs en dto/,
 * la lógica en service/ y reemplazá el throw.
 */
@RestController
public class PublicacionController {

    /** D3 · GET /prestadores/{uuid}/publicaciones · CU11 · Trabajos realizados, solo lectura (lo consume el cliente). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/prestadores/{uuid}/publicaciones")
    public ResponseEntity<Void> listarDePrestador(@PathVariable UUID uuid) {
        throw new NoImplementadoException("D3");
    }

    /** D8 · GET /prestadores/me/publicaciones · CU10 · Trabajos propios (lo consume el prestador). */
    @PreAuthorize("hasRole('PRESTADOR')")
    @GetMapping("/prestadores/me/publicaciones")
    public ResponseEntity<Void> listarMias() {
        throw new NoImplementadoException("D8");
    }

    /** D4 · POST /publicaciones · CU10 · Puede vincular una solicitud FINALIZADA propia (D07). 201 con Location. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @PostMapping("/publicaciones")
    public ResponseEntity<Void> crear() {
        throw new NoImplementadoException("D4");
    }

    /** D5 · PUT /publicaciones/{uuid} · CU10 · Solo propias; ajena = 404. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @PutMapping("/publicaciones/{uuid}")
    public ResponseEntity<Void> actualizar(@PathVariable UUID uuid) {
        throw new NoImplementadoException("D5");
    }

    /** D6 · DELETE /publicaciones/{uuid} · CU10 · Baja lógica de una propia. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @DeleteMapping("/publicaciones/{uuid}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID uuid) {
        throw new NoImplementadoException("D6");
    }
}
