package ar.edu.iessf.servife.gestion.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.paginacion.Paginacion;
import ar.edu.iessf.servife.common.seguridad.UsuarioActual;
import ar.edu.iessf.servife.gestion.dto.DecisionDeValidacionRequest;
import ar.edu.iessf.servife.gestion.dto.ExpedienteResponse;
import ar.edu.iessf.servife.gestion.dto.PrestadorPendienteResponse;
import ar.edu.iessf.servife.gestion.dto.ValidacionResponse;
import ar.edu.iessf.servife.gestion.service.DocumentoLeido;
import ar.edu.iessf.servife.gestion.service.ServicioDeDocumentos;
import ar.edu.iessf.servife.gestion.service.ServicioDeValidacion;
import jakarta.validation.Valid;

/**
 * Módulo E — Validación de prestadores (dueño: Ignacio Requena) · CU14, D01.
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 */
@PreAuthorize("hasRole('GESTOR')")
@RestController
@RequestMapping("/admin")
public class AdminValidacionController {

    private final ServicioDeValidacion servicio;
    private final ServicioDeDocumentos documentos;
    private final UsuarioActual usuarioActual;

    public AdminValidacionController(ServicioDeValidacion servicio, ServicioDeDocumentos documentos, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.documentos = documentos;
        this.usuarioActual = usuarioActual;
    }

    /** E5 · GET /admin/validaciones · CU14 · Prestadores pendientes de validación, los que esperan hace más primero. */
    @GetMapping("/validaciones")
    public Pagina<PrestadorPendienteResponse> listarPendientes(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Pageable pagina = Paginacion.pedir(page, size, Sort.unsorted());
        return servicio.listarPendientes(pagina);
    }

    /** E6 · PATCH /admin/prestadores/{uuid}/validacion · CU14 · Aprueba o rechaza (motivo opcional), guarda quién decidió y avisa al prestador. 409 si ya fue revisado. */
    @PatchMapping("/prestadores/{uuid}/validacion")
    public ValidacionResponse validar(@PathVariable UUID uuid, @Valid @RequestBody DecisionDeValidacionRequest pedido) {
        return servicio.decidir(uuid, pedido.decision(), pedido.motivo(), usuarioActual.uuid());
    }

    /** E8 · GET /admin/prestadores/{uuid}/documentos · CU14 · El expediente: declaración jurada y documentos, sin bytes. */
    @GetMapping("/prestadores/{uuid}/documentos")
    public ExpedienteResponse obtenerDocumentos(@PathVariable UUID uuid) {
        return documentos.expedienteDe(uuid);
    }

    /**
     * E8b · GET /admin/prestadores/{uuid}/documentos/{uuidDocumento} · CU14 · Bytes de un documento, en streaming
     * y sin caché. Solo GESTOR, nunca por URL pública (.ai/07-security.md).
     */
    @GetMapping("/prestadores/{uuid}/documentos/{uuidDocumento}")
    public ResponseEntity<InputStreamResource> leerDocumento(@PathVariable UUID uuid, @PathVariable UUID uuidDocumento) {
        DocumentoLeido leido = documentos.leer(uuid, uuidDocumento);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(leido.mime()))
            .contentLength(leido.bytes())
            .cacheControl(CacheControl.noStore())
            .body(new InputStreamResource(leido.datos()));
    }
}
