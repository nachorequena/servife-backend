package ar.edu.iessf.servife.gestion.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.gestion.dto.MetricasResponse;
import ar.edu.iessf.servife.gestion.repository.MetricasRepository;

/** E9 · CU12 · Contadores del dashboard del gestor (D05: sin incidencias). */
@Service
public class ServicioDeMetricas {

    private final MetricasRepository metricas;

    public ServicioDeMetricas(MetricasRepository metricas) {
        this.metricas = metricas;
    }

    @Transactional(readOnly = true)
    public MetricasResponse obtener() {
        return metricas.contar();
    }
}
