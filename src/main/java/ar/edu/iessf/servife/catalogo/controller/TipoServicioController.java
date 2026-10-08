package ar.edu.iessf.servife.catalogo.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import ar.edu.iessf.servife.catalogo.dto.TipoServicioRequest;
import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.catalogo.service.TipoServicioService;
import jakarta.validation.Valid;

/**
 * Módulo B — Catálogo y búsqueda (dueño: Juan Pablo Saravia).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * B1 (listar tipos de servicio) es público; B2 a B4 (CU13) son del gestor.
 */
@RestController
@RequestMapping("/tipos-servicio")
public class TipoServicioController {

    private final TipoServicioService servicio;

    public TipoServicioController(TipoServicioService servicio) {
        this.servicio = servicio;
    }

    /** B1 · GET /tipos-servicio · CU04, CU13 · Tipos de servicio activos con su ícono. Público: lo usa el registro del prestador. */
    @PreAuthorize("permitAll()")
    @GetMapping
    public List<TipoServicioResponse> listar() {
        return servicio.listarActivos();
    }

    /** B2 · POST /tipos-servicio · CU13 · Incluye requiereMatricula (D01). 201 con Location; reactiva uno dado de baja con el mismo nombre. */
    @PreAuthorize("hasRole('GESTOR')")
    @PostMapping
    public ResponseEntity<TipoServicioResponse> crear(@Valid @RequestBody TipoServicioRequest pedido) {
        TipoServicioResponse creado = servicio.crear(pedido);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}").buildAndExpand(creado.uuid()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    /** B3 · PUT /tipos-servicio/{uuid} · CU13 · Renombra o cambia el ícono. */
    @PreAuthorize("hasRole('GESTOR')")
    @PutMapping("/{uuid}")
    public TipoServicioResponse actualizar(@PathVariable UUID uuid, @Valid @RequestBody TipoServicioRequest pedido) {
        return servicio.actualizar(uuid, pedido);
    }

    /** B4 · DELETE /tipos-servicio/{uuid} · CU13 · Baja lógica. 409 si tiene prestadores activos. */
    @PreAuthorize("hasRole('GESTOR')")
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID uuid) {
        servicio.eliminar(uuid);
        return ResponseEntity.noContent().build();
    }
}
