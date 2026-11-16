package ar.edu.iessf.servife.identidad.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.identidad.dto.DireccionRequest;
import ar.edu.iessf.servife.identidad.dto.DireccionResponse;
import ar.edu.iessf.servife.identidad.service.ServicioDeDirecciones;
import jakarta.validation.Valid;

/**
 * Módulo A — Direcciones guardadas del cliente, para elegir una al pedir un servicio (C1).
 * La principal se refleja en la dirección de A4/A6.
 */
@PreAuthorize("hasRole('CLIENTE')")
@RestController
@RequestMapping("/clientes/me/direcciones")
public class DireccionController {

    private final ServicioDeDirecciones servicio;
    private final UsuarioActual usuarioActual;

    public DireccionController(ServicioDeDirecciones servicio, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
    }

    /** A10 · GET /clientes/me/direcciones · La principal primero. */
    @GetMapping
    public List<DireccionResponse> listar() {
        return servicio.listar(usuarioActual.uuid());
    }

    /** A11 · POST /clientes/me/direcciones · La primera queda como principal. 201 con Location. */
    @PostMapping
    public ResponseEntity<DireccionResponse> crear(@Valid @RequestBody DireccionRequest pedido) {
        DireccionResponse creada = servicio.crear(usuarioActual.uuid(), pedido);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}")
            .buildAndExpand(creada.uuid()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    /** A12 · PUT /clientes/me/direcciones/{uuid} · Edita alias y dirección. 404 si no es tuya. */
    @PutMapping("/{uuid}")
    public DireccionResponse actualizar(@PathVariable UUID uuid, @Valid @RequestBody DireccionRequest pedido) {
        return servicio.actualizar(usuarioActual.uuid(), uuid, pedido);
    }

    /** A13 · DELETE /clientes/me/direcciones/{uuid} · Baja lógica; si era la principal, otra pasa a serlo. 204. */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID uuid) {
        servicio.eliminar(usuarioActual.uuid(), uuid);
        return ResponseEntity.noContent().build();
    }

    /** A14 · PATCH /clientes/me/direcciones/{uuid}/principal · La marca como principal. */
    @PatchMapping("/{uuid}/principal")
    public DireccionResponse marcarPrincipal(@PathVariable UUID uuid) {
        return servicio.marcarPrincipal(usuarioActual.uuid(), uuid);
    }
}
