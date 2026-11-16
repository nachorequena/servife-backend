package ar.edu.iessf.servife.gestion.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iessf.servife.gestion.dto.MetricasDetalleResponse;
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

    /** E9b · GET /admin/metricas/detalle · CU12 · Trabajo por servicio, por estado, por mes y top prestadores del período. Sin fechas: últimos 6 meses. */
    @GetMapping("/detalle")
    public MetricasDetalleResponse obtenerDetalle(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.obtenerDetalle(desde, hasta);
    }
}
