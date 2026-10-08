package ar.edu.iessf.servife.catalogo.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.domain.Disponibilidad;
import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.dto.PrestadorDetalleResponse;
import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.catalogo.repository.DisponibilidadRepository;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;

/** B6 · CU05: ficha pública del prestador. El propio prestador ve su perfil en /me/perfil. */
@Service
public class ServicioDeFichaDePrestador {

    private static final String CONTAR_REALIZADOS = """
        SELECT count(*) FROM solicitudes_servicio
        WHERE id_prestador = (SELECT id_prestador FROM prestadores WHERE uuid = :uuid)
          AND estado IN ('FINALIZADA', 'VALORADA') AND eliminado_en IS NULL
        """;

    private final PrestadorRepository prestadores;
    private final DisponibilidadRepository disponibilidades;
    private final NamedParameterJdbcTemplate jdbc;

    public ServicioDeFichaDePrestador(PrestadorRepository prestadores, DisponibilidadRepository disponibilidades,
            NamedParameterJdbcTemplate jdbc) {
        this.prestadores = prestadores;
        this.disponibilidades = disponibilidades;
        this.jdbc = jdbc;
    }

    /** 404 si no existe, está dado de baja, no está APROBADO o su cuenta está SUSPENDIDA. */
    @Transactional(readOnly = true)
    public PrestadorDetalleResponse obtener(UUID uuid) {
        Prestador p = prestadores.findByUuidAndEliminadoEnIsNull(uuid)
            .filter(x -> x.getEstadoValidacion() == EstadoValidacion.APROBADO
                && x.getEstadoCuenta() == EstadoCuenta.ACTIVA)
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));
        TipoServicio t = p.getTipoServicio();
        List<Integer> dias = disponibilidades.findByPrestadorAndEliminadoEnIsNullOrderByDiaSemana(p).stream()
            .map(Disponibilidad::getDiaSemana).toList();
        Long realizados = jdbc.queryForObject(CONTAR_REALIZADOS, Map.of("uuid", uuid), Long.class);
        return new PrestadorDetalleResponse(p.getUuid(), p.getNombreApellido(),
            new TipoServicioResponse(t.getUuid(), t.getNombre(), t.getIcono(), t.isRequiereMatricula()),
            p.getZona(), p.getDescripcion(), dias, p.getValoracionPromedio(),
            realizados == null ? 0 : realizados, true);
    }
}
