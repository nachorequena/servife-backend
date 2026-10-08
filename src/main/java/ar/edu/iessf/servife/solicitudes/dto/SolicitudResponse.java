package ar.edu.iessf.servife.solicitudes.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.solicitudes.domain.AccionSobreSolicitud;
import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;

/** Solicitud tal como la ve la app; accionesDisponibles son las que el usuario actual puede hacer ahora. */
public record SolicitudResponse(UUID uuid, EstadoSolicitud estado, ParteResponse cliente, ParteResponse prestador,
        TipoServicioResponse tipoServicio, LocalDate fechaDeseada, String horaPreferida, String direccion,
        String descripcion, List<UUID> imagenIds, Long precioAcordado, String motivo, String canceladaPor,
        Instant creadoEn, Instant actualizadoEn, List<AccionSobreSolicitud> accionesDisponibles) {
}
