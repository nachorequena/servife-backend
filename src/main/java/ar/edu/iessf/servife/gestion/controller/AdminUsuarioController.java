package ar.edu.iessf.servife.gestion.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.error.NoImplementadoException;

/**
 * Módulo E — Gestión, validación y mensajería (dueño: Ignacio Requena).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * Cada método es un stub que responde 501 hasta que se implemente: definí los DTOs en dto/,
 * la lógica en service/ y reemplazá el throw.
 */
@PreAuthorize("hasRole('GESTOR')")
@RestController
@RequestMapping("/admin/usuarios")
public class AdminUsuarioController {

    /** E3 · GET /admin/usuarios · CU12 · Filtros rol, estado, texto. Paginado. */
    @GetMapping
    public ResponseEntity<Void> listar() {
        throw new NoImplementadoException("E3");
    }

    /** E13 · POST /admin/usuarios · CU15 · Única vía para crear un GESTOR (D06). 201 con Location. */
    @PostMapping
    public ResponseEntity<Void> crear() {
        throw new NoImplementadoException("E13");
    }

    /** E14 · PUT /admin/usuarios/{uuid} · CU17 · Modifica datos del usuario. */
    @PutMapping("/{uuid}")
    public ResponseEntity<Void> actualizar(@PathVariable UUID uuid) {
        throw new NoImplementadoException("E14");
    }

    /** E4 · PATCH /admin/usuarios/{uuid}/estado · CU12 · Suspende o reactiva. Exige motivo y avisa por correo. */
    @PatchMapping("/{uuid}/estado")
    public ResponseEntity<Void> cambiarEstado(@PathVariable UUID uuid) {
        throw new NoImplementadoException("E4");
    }

    /** E15 · DELETE /admin/usuarios/{uuid} · CU16 · Baja lógica (eliminado_en). */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID uuid) {
        throw new NoImplementadoException("E15");
    }
}
