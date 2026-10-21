package ar.edu.iessf.servife.gestion.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.paginacion.Paginacion;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.gestion.dto.ActualizarUsuarioAdminRequest;
import ar.edu.iessf.servife.gestion.dto.CambioDeEstadoDeCuentaRequest;
import ar.edu.iessf.servife.gestion.dto.CrearGestorRequest;
import ar.edu.iessf.servife.gestion.dto.UsuarioAdminResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeUsuarios;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import jakarta.validation.Valid;

/**
 * Módulo E — Gestión de usuarios (dueño: Ignacio Requena) · CU12 y sus extensiones CU15 a CU17 (D06).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 */
@PreAuthorize("hasRole('GESTOR')")
@RestController
@RequestMapping("/admin/usuarios")
public class AdminUsuarioController {

    private final ServicioDeUsuarios servicio;
    private final UsuarioActual usuarioActual;

    public AdminUsuarioController(ServicioDeUsuarios servicio, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
    }

    /** E3 · GET /admin/usuarios · CU12 · Filtros rol, estado y texto (nombre, email o uuid). Paginado, más nuevos primero. */
    @GetMapping
    public Pagina<UsuarioAdminResponse> listar(@RequestParam(required = false) Rol rol,
            @RequestParam(required = false) EstadoCuenta estado, @RequestParam(required = false) String texto,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return servicio.listar(rol, estado, texto, Paginacion.pedir(page, size, Sort.unsorted()));
    }

    /** E13 · POST /admin/usuarios · CU15 · Única vía para crear un GESTOR (D06). 201 con Location. */
    @PostMapping
    public ResponseEntity<UsuarioAdminResponse> crear(@Valid @RequestBody CrearGestorRequest pedido) {
        UsuarioAdminResponse creado = servicio.crearGestor(pedido);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uuid}").buildAndExpand(creado.uuid()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    /** E14 · PUT /admin/usuarios/{uuid} · CU17 · Nombre, email y teléfono. */
    @PutMapping("/{uuid}")
    public UsuarioAdminResponse actualizar(@PathVariable UUID uuid, @Valid @RequestBody ActualizarUsuarioAdminRequest pedido) {
        return servicio.actualizar(uuid, pedido);
    }

    /** E4 · PATCH /admin/usuarios/{uuid}/estado · CU12 · Suspende (con motivo) o reactiva, y avisa por correo. */
    @PatchMapping("/{uuid}/estado")
    public UsuarioAdminResponse cambiarEstado(@PathVariable UUID uuid, @Valid @RequestBody CambioDeEstadoDeCuentaRequest pedido) {
        return servicio.cambiarEstado(uuid, pedido, usuarioActual.uuid());
    }

    /** E15 · DELETE /admin/usuarios/{uuid} · CU16 · Baja lógica (eliminado_en). 204. */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID uuid) {
        servicio.darDeBaja(uuid, usuarioActual.uuid());
        return ResponseEntity.noContent().build();
    }
}
