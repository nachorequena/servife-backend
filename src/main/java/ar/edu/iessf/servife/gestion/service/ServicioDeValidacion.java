package ar.edu.iessf.servife.gestion.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.paginacion.Pagina;
import ar.edu.iessf.servife.gestion.dto.Decision;
import ar.edu.iessf.servife.gestion.dto.PrestadorPendienteResponse;
import ar.edu.iessf.servife.gestion.dto.ValidacionResponse;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;

/**
 * E5 y E6 · CU14 (versión mínima, sin documentos): lista los prestadores pendientes y deja que el
 * gestor los apruebe o rechace, avisándoles. La suspensión de la cuenta no interviene: el gestor
 * decide la validación de forma independiente.
 */
@Service
public class ServicioDeValidacion {

    private final PrestadorRepository prestadores;
    private final Avisos avisos;
    private final Clock reloj;

    public ServicioDeValidacion(PrestadorRepository prestadores, Avisos avisos, Clock reloj) {
        this.prestadores = prestadores;
        this.avisos = avisos;
        this.reloj = reloj;
    }

    /** Pendientes no dados de baja, los más viejos primero (el orden lo impone el servicio). */
    @Transactional(readOnly = true)
    public Pagina<PrestadorPendienteResponse> listarPendientes(Pageable pagina) {
        Pageable ordenada = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by("creadoEn", "uuid"));
        return Pagina.de(prestadores.findByEstadoValidacionAndEliminadoEnIsNull(EstadoValidacion.PENDIENTE, ordenada),
            ServicioDeValidacion::aRespuesta);
    }

    /**
     * Aprueba o rechaza a un prestador PENDIENTE y le deja un aviso en la misma transacción.
     * 404 si no existe o está dado de baja; 409 VALIDACION_YA_RESUELTA si ya no está pendiente.
     */
    @Transactional
    public ValidacionResponse decidir(UUID uuid, Decision decision, String motivo) {
        Prestador existente = prestadores.findByUuidAndEliminadoEnIsNull(uuid)
            .orElseThrow(() -> new RecursoNoEncontradoException("No existe."));
        boolean aprobar = decision == Decision.APROBAR;
        EstadoValidacion nuevo = aprobar ? EstadoValidacion.APROBADO : EstadoValidacion.RECHAZADO;

        if (prestadores.resolverSiPendiente(existente.getId(), nuevo, reloj.instant()) != 1) {
            throw new ConflictoException("VALIDACION_YA_RESUELTA", "Ese prestador ya fue revisado.");
        }

        String motivoLimpio = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (aprobar) {
            avisos.avisar(existente, TipoDeAviso.PERFIL_APROBADO, "Tu perfil fue aprobado",
                "Tu perfil fue aprobado. Ya aparecés en las búsquedas.", null);
        } else {
            avisos.avisar(existente, TipoDeAviso.PERFIL_RECHAZADO, "Tu perfil fue rechazado",
                "Tu perfil no fue aprobado." + (motivoLimpio != null ? " Motivo: " + motivoLimpio : ""), null);
        }
        return new ValidacionResponse(uuid, nuevo);
    }

    private static PrestadorPendienteResponse aRespuesta(Prestador p) {
        var t = p.getTipoServicio();
        return new PrestadorPendienteResponse(p.getUuid(), p.getNombreApellido(), p.getEmail(),
            new TipoServicioResponse(t.getUuid(), t.getNombre(), t.getIcono(), t.isRequiereMatricula()),
            p.getCreadoEn());
    }
}
