package ar.edu.iessf.servife.gestion.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.common.error.NoImplementadoException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.common.paginacion.Paginacion;
import ar.edu.iessf.servife.gestion.dto.DecisionDeValidacionRequest;
import ar.edu.iessf.servife.gestion.dto.PrestadorPendienteResponse;
import ar.edu.iessf.servife.gestion.dto.ValidacionResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeValidacion;
import jakarta.validation.Valid;

/**
 * Módulo E — Gestión, validación y mensajería (dueño: Ignacio Requena).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 * E5 y E6 (aprobación mínima de prestadores, sin documentos) están implementados; E8 sigue
 * siendo un stub que responde 501 hasta el expediente completo (D01, Sprint 5).
 */
@PreAuthorize("hasRole('GESTOR')")
@RestController
@RequestMapping("/admin")
public class AdminValidacionController {

    private final ServicioDeValidacion servicio;

    public AdminValidacionController(ServicioDeValidacion servicio) {
        this.servicio = servicio;
    }

    /** E5 · GET /admin/validaciones · CU14 · Prestadores pendientes de validación, los más viejos primero. */
    @GetMapping("/validaciones")
    public Pagina<PrestadorPendienteResponse> listarPendientes(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Pageable pagina = Paginacion.pedir(page, size, Sort.unsorted());
        return servicio.listarPendientes(pagina);
    }

    /** E6 · PATCH /admin/prestadores/{uuid}/validacion · CU14 · Aprueba o rechaza (motivo opcional) y avisa al prestador. 409 si ya fue revisado. */
    @PatchMapping("/prestadores/{uuid}/validacion")
    public ValidacionResponse validar(@PathVariable UUID uuid, @Valid @RequestBody DecisionDeValidacionRequest pedido) {
        return servicio.decidir(uuid, pedido.decision(), pedido.motivo());
    }

    /** E8 · GET /admin/prestadores/{uuid}/documentos · CU14 · Descarga controlada del expediente. Nunca por URL pública (.ai/07-security.md). */
    @GetMapping("/prestadores/{uuid}/documentos")
    public ResponseEntity<Void> obtenerDocumentos(@PathVariable UUID uuid) {
        throw new NoImplementadoException("E8");
    }
}
