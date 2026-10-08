package ar.edu.iessf.servife.catalogo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.domain.Disponibilidad;
import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.dto.ActualizarPerfilDePrestadorRequest;
import ar.edu.iessf.servife.catalogo.dto.DisponibilidadRequest;
import ar.edu.iessf.servife.catalogo.dto.DisponibilidadResponse;
import ar.edu.iessf.servife.catalogo.dto.PerfilDeServicioResponse;
import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.catalogo.repository.DisponibilidadRepository;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.identidad.service.Cuentas;

/** Perfil de servicio (B7) y disponibilidad semanal (B8, C5) del prestador. */
@Service
public class ServicioDePerfilDePrestador {

    private final Cuentas cuentas;
    private final PrestadorRepository prestadores;
    private final TipoServicioRepository tiposServicio;
    private final DisponibilidadRepository disponibilidades;

    public ServicioDePerfilDePrestador(Cuentas cuentas, PrestadorRepository prestadores,
            TipoServicioRepository tiposServicio, DisponibilidadRepository disponibilidades) {
        this.cuentas = cuentas;
        this.prestadores = prestadores;
        this.tiposServicio = tiposServicio;
        this.disponibilidades = disponibilidades;
    }

    /** GET /prestadores/me/perfil. */
    @Transactional(readOnly = true)
    public PerfilDeServicioResponse obtenerPerfil(UUID idPrestador) {
        return aRespuesta(prestadorActual(idPrestador));
    }

    /** B7: guarda rubro, zona, punto (redondeado a ~1 km), radio y descripción. Cambiar de rubro lo manda a revisión (PENDIENTE). */
    @Transactional
    public PerfilDeServicioResponse actualizarPerfil(UUID idPrestador, ActualizarPerfilDePrestadorRequest pedido) {
        Prestador prestador = prestadorActual(idPrestador);
        TipoServicio tipo = tiposServicio.findByUuidAndEliminadoEnIsNull(pedido.idTipoServicio())
            .orElseThrow(() -> new ValidacionException("idTipoServicio", "El rubro elegido no existe"));
        if ((pedido.lat() == null) != (pedido.lng() == null)) {
            throw new ValidacionException("lat", "Indicá latitud y longitud juntas, o ninguna");
        }
        prestador.cambiarTipoServicio(tipo);
        prestador.actualizarPerfil(textoOnulo(pedido.zona()), aproximar(pedido.lat()), aproximar(pedido.lng()), pedido.radioKm(),
            textoOnulo(pedido.descripcion()));
        return aRespuesta(prestadores.saveAndFlush(prestador));
    }

    /** B8: reemplaza el set de días (borrado físico + alta en la misma transacción). Repetidos se descartan. */
    @Transactional
    public DisponibilidadResponse reemplazarDisponibilidad(UUID idPrestador, DisponibilidadRequest pedido) {
        Prestador prestador = prestadorActual(idPrestador);
        List<Integer> dias = pedido.dias().stream().distinct().sorted().toList();
        disponibilidades.borrarDelPrestador(prestador);
        disponibilidades.saveAllAndFlush(dias.stream().map(d -> new Disponibilidad(prestador, d)).toList());
        return new DisponibilidadResponse(dias);
    }

    /** C5: días en que trabaja cualquier prestador (404 si no existe o está dado de baja). */
    @Transactional(readOnly = true)
    public DisponibilidadResponse obtenerDisponibilidad(UUID idPrestador) {
        Prestador prestador = prestadores.findByUuidAndEliminadoEnIsNull(idPrestador)
            .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos al prestador"));
        return new DisponibilidadResponse(disponibilidades.findByPrestadorAndEliminadoEnIsNullOrderByDiaSemana(prestador)
            .stream().map(Disponibilidad::getDiaSemana).toList());
    }

    private Prestador prestadorActual(UUID idPrestador) {
        return cuentas.buscarPorUuid(idPrestador, Rol.PRESTADOR)
            .filter(Prestador.class::isInstance).map(Prestador.class::cast)
            .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos al prestador"));
    }

    /** Privacidad: el punto se guarda con 2 decimales (~1 km) para que la búsqueda no revele la ubicación exacta. */
    private static BigDecimal aproximar(BigDecimal coordenada) {
        return coordenada == null ? null : coordenada.setScale(2, RoundingMode.HALF_UP);
    }

    private static String textoOnulo(String texto) {
        if (texto == null) {
            return null;
        }
        String recortado = texto.trim();
        return recortado.isEmpty() ? null : recortado;
    }

    private static PerfilDeServicioResponse aRespuesta(Prestador p) {
        TipoServicio t = p.getTipoServicio();
        return new PerfilDeServicioResponse(t.getUuid(),
            new TipoServicioResponse(t.getUuid(), t.getNombre(), t.getIcono(), t.isRequiereMatricula()),
            p.getZona(), p.getLat(), p.getLng(), p.getRadioKm(), p.getDescripcion(), p.getEstadoValidacion());
    }
}
