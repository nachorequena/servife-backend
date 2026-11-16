package ar.edu.iessf.servife.gestion.service;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.gestion.dto.MetricasDePrestadorResponse;
import ar.edu.iessf.servife.gestion.repository.MetricasRepository;
import ar.edu.iessf.servife.gestion.service.ServicioDeMetricas.Periodo;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;

/** E16 · Dashboard del prestador: solo sus propias solicitudes. Mismas reglas de período que E9b. */
@Service
public class ServicioDeMetricasDePrestador {

    private static final int TOP_CLIENTES = 5;

    private final MetricasRepository metricas;
    private final ServicioDeMetricas metricasDelGestor;
    private final PrestadorRepository prestadores;

    public ServicioDeMetricasDePrestador(MetricasRepository metricas, ServicioDeMetricas metricasDelGestor,
            PrestadorRepository prestadores) {
        this.metricas = metricas;
        this.metricasDelGestor = metricasDelGestor;
        this.prestadores = prestadores;
    }

    @Transactional(readOnly = true)
    public MetricasDePrestadorResponse obtener(UUID uuidPrestador, LocalDate desde, LocalDate hasta) {
        Prestador prestador = prestadores.findByUuidAndEliminadoEnIsNull(uuidPrestador)
            .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos tu cuenta."));
        Periodo periodo = metricasDelGestor.periodo(desde, hasta);
        Long id = prestador.getId();
        return new MetricasDePrestadorResponse(periodo.desde(), periodo.hasta(),
            metricas.totalSolicitudes(periodo.desde(), periodo.hasta(), id),
            ServicioDeMetricas.ordenarPorEstado(metricas.porEstado(periodo.desde(), periodo.hasta(), id)),
            metricas.porCliente(periodo.desde(), periodo.hasta(), id, TOP_CLIENTES),
            metricas.porServicio(periodo.desde(), periodo.hasta(), id),
            ServicioDeMetricas.completarMeses(periodo, metricas.porMes(periodo.desde(), periodo.hasta(), id)),
            prestador.getValoracionPromedio());
    }
}
