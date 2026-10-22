package ar.edu.iessf.servife.reputacion.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.reputacion.dto.PublicacionRequest;
import ar.edu.iessf.servife.reputacion.dto.PublicacionResponse;
import ar.edu.iessf.servife.reputacion.service.ServicioDePublicaciones;
import jakarta.validation.Valid;

/**
 * Módulo D — Reputación y trabajos (dueño: Facundo Bustamante).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * D3, D4, D5, D6 y D8 están implementados.
 */
@RestController
public class PublicacionController {

    private final ServicioDePublicaciones servicio;

    public PublicacionController(ServicioDePublicaciones servicio) {
        this.servicio = servicio;
    }

    /** D3 · GET /prestadores/{uuid}/publicaciones · CU11 · Trabajos realizados, solo lectura (lo consume el cliente). Paginado. 404 si el prestador no está visible (criterio de B6). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/prestadores/{uuid}/publicaciones")
    public Pagina<PublicacionResponse> listarDePrestador(@PathVariable UUID uuid,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return servicio.listarDePrestador(uuid, page, size);
    }

    /** D8 · GET /prestadores/me/publicaciones · CU10 · Trabajos propios (lo consume el prestador). Paginado. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @GetMapping("/prestadores/me/publicaciones")
    public Pagina<PublicacionResponse> listarMias(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return servicio.listarMias(page, size);
    }

    /** D4 · POST /publicaciones · CU10 · Puede vincular una solicitud propia FINALIZADA o VALORADA (D07). 201 con Location. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @PostMapping("/publicaciones")
    public ResponseEntity<PublicacionResponse> crear(@Valid @RequestBody PublicacionRequest pedido) {
        PublicacionResponse creada = servicio.crear(pedido);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
            .buildAndExpand(creada.uuid()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    /** D5 · PUT /publicaciones/{uuid} · CU10 · Solo propias; ajena = 404. Reemplaza todo, imágenes incluidas. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @PutMapping("/publicaciones/{uuid}")
    public PublicacionResponse actualizar(@PathVariable UUID uuid, @Valid @RequestBody PublicacionRequest pedido) {
        return servicio.actualizar(uuid, pedido);
    }

    /** D6 · DELETE /publicaciones/{uuid} · CU10 · Baja lógica de una propia; ajena = 404. 204. */
    @PreAuthorize("hasRole('PRESTADOR')")
    @DeleteMapping("/publicaciones/{uuid}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID uuid) {
        servicio.eliminar(uuid);
        return ResponseEntity.noContent().build();
    }
}
