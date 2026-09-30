package ar.edu.iessf.servife.catalogo.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.error.NoImplementadoException;

/**
 * Módulo B — Catálogo y búsqueda (dueño: Juan Pablo Saravia).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * Cada método es un stub que responde 501 hasta que se implemente: definí los DTOs en dto/,
 * la lógica en service/ y reemplazá el throw.
 */
@RestController
@RequestMapping("/tipos-servicio")
public class TipoServicioController {

    /** B1 · GET /tipos-servicio · CU04, CU13 · Tipos de servicio activos con su ícono. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<Void> listar() {
        throw new NoImplementadoException("B1");
    }

    /** B2 · POST /tipos-servicio · CU13 · Incluye requiereMatricula (D01). 201 con Location. */
    @PreAuthorize("hasRole('GESTOR')")
    @PostMapping
    public ResponseEntity<Void> crear() {
        throw new NoImplementadoException("B2");
    }

    /** B3 · PUT /tipos-servicio/{uuid} · CU13 · Renombra o cambia el ícono. */
    @PreAuthorize("hasRole('GESTOR')")
    @PutMapping("/{uuid}")
    public ResponseEntity<Void> actualizar(@PathVariable UUID uuid) {
        throw new NoImplementadoException("B3");
    }

    /** B4 · DELETE /tipos-servicio/{uuid} · CU13 · Baja lógica. 409 si tiene prestadores activos. */
    @PreAuthorize("hasRole('GESTOR')")
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID uuid) {
        throw new NoImplementadoException("B4");
    }
}
