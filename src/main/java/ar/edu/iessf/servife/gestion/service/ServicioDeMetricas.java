package ar.edu.iessf.servife.gestion.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.gestion.dto.ConteoPorMes;
import ar.edu.iessf.servife.gestion.dto.ConteoPorNombre;
import ar.edu.iessf.servife.gestion.dto.MetricasDetalleResponse;
import ar.edu.iessf.servife.gestion.dto.MetricasResponse;
import ar.edu.iessf.servife.gestion.repository.MetricasRepository;
import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;

/** E9 · CU12 · Contadores del dashboard del gestor (D05: sin incidencias). E9b · gráficos del período. */
@Service
public class ServicioDeMetricas {

    static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    /** Período por defecto: los últimos 6 meses calendario, contando el actual. */
    static final int MESES_POR_DEFECTO = 6;

    /** Tope del período, para que el gráfico por mes siga siendo legible. */
    static final int MESES_MAXIMOS = 24;

    private static final int TOP_PRESTADORES = 5;

    private final MetricasRepository metricas;
    private final Clock reloj;

    public ServicioDeMetricas(MetricasRepository metricas, Clock reloj) {
        this.metricas = metricas;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public MetricasResponse obtener() {
        return metricas.contar();
    }

    /** E9b. Sin fechas: últimos 6 meses. desde posterior a hasta, o más de 24 meses: 400. */
    @Transactional(readOnly = true)
    public MetricasDetalleResponse obtenerDetalle(LocalDate desde, LocalDate hasta) {
        Periodo periodo = periodo(desde, hasta);
        return new MetricasDetalleResponse(periodo.desde(), periodo.hasta(),
            metricas.totalSolicitudes(periodo.desde(), periodo.hasta(), null),
            metricas.porServicio(periodo.desde(), periodo.hasta(), null),
            ordenarPorEstado(metricas.porEstado(periodo.desde(), periodo.hasta(), null)),
            completarMeses(periodo, metricas.porMes(periodo.desde(), periodo.hasta(), null)),
            metricas.topPrestadores(periodo.desde(), periodo.hasta(), TOP_PRESTADORES));
    }

    /** Período validado; lo comparte el dashboard del prestador (E16). */
    Periodo periodo(LocalDate desde, LocalDate hasta) {
        LocalDate hoy = LocalDate.now(reloj.withZone(ARGENTINA));
        LocalDate fin = hasta == null ? hoy : hasta;
        LocalDate inicio = desde == null ? fin.withDayOfMonth(1).minusMonths(MESES_POR_DEFECTO - 1L) : desde;
        if (inicio.isAfter(fin)) {
            throw new ValidacionException("desde", "no puede ser posterior a hasta");
        }
        if (inicio.isBefore(fin.minusMonths(MESES_MAXIMOS))) {
            throw new ValidacionException("desde", "el período no puede superar " + MESES_MAXIMOS + " meses");
        }
        return new Periodo(inicio, fin);
    }

    /** Orden de la máquina de estados, no alfabético. */
    static List<ConteoPorNombre> ordenarPorEstado(List<ConteoPorNombre> conteos) {
        return conteos.stream()
            .sorted(Comparator.comparingInt(c -> EstadoSolicitud.valueOf(c.nombre()).ordinal()))
            .toList();
    }

    /** Un punto por mes del período, con 0 en los meses sin solicitudes. */
    static List<ConteoPorMes> completarMeses(Periodo periodo, List<ConteoPorNombre> conteos) {
        Map<String, Long> porMes = conteos.stream()
            .collect(Collectors.toMap(ConteoPorNombre::nombre, ConteoPorNombre::cantidad));
        List<ConteoPorMes> meses = new ArrayList<>();
        for (YearMonth mes = YearMonth.from(periodo.desde()); !mes.isAfter(YearMonth.from(periodo.hasta()));
                mes = mes.plusMonths(1)) {
            meses.add(new ConteoPorMes(mes.toString(), porMes.getOrDefault(mes.toString(), 0L)));
        }
        return meses;
    }

    record Periodo(LocalDate desde, LocalDate hasta) {
    }
}
