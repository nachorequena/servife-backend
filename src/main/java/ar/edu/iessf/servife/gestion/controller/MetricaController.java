package ar.edu.iessf.servife.gestion.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.gestion.dto.MetricasResponse;
import ar.edu.iessf.servife.gestion.service.ServicioDeMetricas;

/**
 * Módulo E — Métricas del dashboard (dueño: Ignacio Requena).
 * Endpoints según servife-ia/.ai/05-api-contract.md; los IDs son los del prototipo.
 */
@PreAuthorize("hasRole('GESTOR')")
@RestController
@RequestMapping("/admin/metricas")
public class MetricaController {

    private final ServicioDeMetricas servicio;

    public MetricaController(ServicioDeMetricas servicio) {
        this.servicio = servicio;
    }

    /** E9 · GET /admin/metricas · CU12 · Servicios activos, solicitudes pendientes y validaciones pendientes. Sin incidencias (D05). */
    @GetMapping
    public MetricasResponse obtener() {
        return servicio.obtener();
    }
}
