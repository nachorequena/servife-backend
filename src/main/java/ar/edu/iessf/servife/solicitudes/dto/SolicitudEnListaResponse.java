package ar.edu.iessf.servife.solicitudes.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import ar.edu.iessf.servife.catalogo.dto.TipoServicioResponse;
import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;

/** Fila de C2. La contraparte es la otra parte; la descripción viene recortada a 140 caracteres. */
public record SolicitudEnListaResponse(UUID uuid, EstadoSolicitud estado, ParteResponse contraparte,
        TipoServicioResponse tipoServicio, LocalDate fechaDeseada, String horaPreferida, String descripcion,
        String direccion, Instant creadoEn) {
}
