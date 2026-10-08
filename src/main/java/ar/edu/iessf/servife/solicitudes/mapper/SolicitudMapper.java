package ar.edu.iessf.servife.solicitudes.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.solicitudes.domain.AccionSobreSolicitud;
import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;
import ar.edu.iessf.servife.solicitudes.domain.MaquinaDeEstados;
import ar.edu.iessf.servife.solicitudes.domain.Solicitud;
import ar.edu.iessf.servife.solicitudes.dto.ParteResponse;
import ar.edu.iessf.servife.solicitudes.dto.SolicitudResponse;

/** Solicitud a SolicitudResponse. Lo usan C1, C2 y C3. */
public final class SolicitudMapper {

    /** Desde estos estados los teléfonos de las partes ya se muestran. */
    private static final Set<EstadoSolicitud> CON_TELEFONO = Set.of(EstadoSolicitud.ACEPTADA,
        EstadoSolicitud.EN_CURSO, EstadoSolicitud.FINALIZADA, EstadoSolicitud.VALORADA);

    private SolicitudMapper() {
    }

    /**
     * @param imagenIds uuid de los archivos adjuntos
     * @param rolActual rol del usuario que consulta, para calcular las acciones disponibles
     * @param hoy el día de hoy en Argentina: antes de la fecha deseada no se ofrece INICIAR (C4 contestaría 409)
     */
    public static SolicitudResponse aRespuesta(Solicitud s, List<UUID> imagenIds, Rol rolActual, LocalDate hoy) {
        boolean conTelefono = CON_TELEFONO.contains(s.getEstado());
        TipoServicio t = s.getTipoServicio();
        return new SolicitudResponse(s.getUuid(), s.getEstado(),
            parte(s.getCliente(), s.getCliente().getTelefono(), conTelefono),
            parte(s.getPrestador(), s.getPrestador().getTelefono(), conTelefono),
            tipo(t),
            s.getFechaDeseada(), s.getHoraPreferida(), s.getDireccion(), s.getDescripcion(), imagenIds,
            s.getPrecioAcordado(), s.getMotivo(),
            s.getCanceladaPor() == null ? null : s.getCanceladaPor().name(),
            s.getCreadoEn(), s.getActualizadoEn(),
            accionesDisponibles(s, rolActual, hoy));
    }

    private static List<AccionSobreSolicitud> accionesDisponibles(Solicitud s, Rol rolActual, LocalDate hoy) {
        boolean antesDeLaFecha = s.getFechaDeseada() != null && hoy.isBefore(s.getFechaDeseada());
        return MaquinaDeEstados.accionesDisponibles(s.getEstado(), rolActual).stream()
            .filter(a -> !(a == AccionSobreSolicitud.INICIAR && antesDeLaFecha))
            .toList();
    }

    /** La otra parte de la solicitud, vista desde rolActual (el cliente ve al prestador y viceversa). */
    public static ParteResponse contraparte(Solicitud s, Rol rolActual) {
        boolean conTelefono = CON_TELEFONO.contains(s.getEstado());
        return rolActual == Rol.CLIENTE
            ? parte(s.getPrestador(), s.getPrestador().getTelefono(), conTelefono)
            : parte(s.getCliente(), s.getCliente().getTelefono(), conTelefono);
    }

    public static TipoServicioResponse tipo(TipoServicio t) {
        return new TipoServicioResponse(t.getUuid(), t.getNombre(), t.getIcono(), t.isRequiereMatricula());
    }

    private static ParteResponse parte(Cuenta cuenta, String telefono, boolean conTelefono) {
        return new ParteResponse(cuenta.getUuid(), cuenta.getNombreApellido(), conTelefono ? telefono : null);
    }
}
