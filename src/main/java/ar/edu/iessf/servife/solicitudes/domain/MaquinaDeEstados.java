package ar.edu.iessf.servife.solicitudes.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import ar.edu.iessf.servife.common.seguridad.Rol;

/**
 * Tabla explícita (acción, origen) → (destino, roles permitidos) de 02-context. Java puro, sin Spring.
 * <p>
 * No incluye la regla de fechas de INICIAR (solo desde la fecha deseada, en hora de Argentina):
 * necesita el Clock y fecha_deseada, así que la verifica el servicio (Task 18) antes de aplicar la
 * transición. FINALIZADA → VALORADA y los casos de D07 quedan para el Sprint 4.
 */
public final class MaquinaDeEstados {

    private record Transicion(AccionSobreSolicitud accion, EstadoSolicitud origen, EstadoSolicitud destino,
            Set<Rol> roles) {
    }

    private static final List<Transicion> TABLA = List.of(
        new Transicion(AccionSobreSolicitud.ACEPTAR, EstadoSolicitud.PENDIENTE, EstadoSolicitud.ACEPTADA,
            Set.of(Rol.PRESTADOR)),
        new Transicion(AccionSobreSolicitud.RECHAZAR, EstadoSolicitud.PENDIENTE, EstadoSolicitud.RECHAZADA,
            Set.of(Rol.PRESTADOR)),
        new Transicion(AccionSobreSolicitud.CANCELAR, EstadoSolicitud.PENDIENTE, EstadoSolicitud.CANCELADA,
            Set.of(Rol.CLIENTE)),
        new Transicion(AccionSobreSolicitud.CANCELAR, EstadoSolicitud.ACEPTADA, EstadoSolicitud.CANCELADA,
            Set.of(Rol.CLIENTE, Rol.PRESTADOR)),
        new Transicion(AccionSobreSolicitud.INICIAR, EstadoSolicitud.ACEPTADA, EstadoSolicitud.EN_CURSO,
            Set.of(Rol.PRESTADOR)),
        new Transicion(AccionSobreSolicitud.FINALIZAR, EstadoSolicitud.EN_CURSO, EstadoSolicitud.FINALIZADA,
            Set.of(Rol.PRESTADOR)));

    private MaquinaDeEstados() {
    }

    /**
     * Estado al que pasa la solicitud.
     *
     * @throws TransicionInvalidaException si la acción no existe para el estado de origen (409)
     * @throws ActorNoPermitidoException si existe pero el rol no puede hacerla (403)
     */
    public static EstadoSolicitud destino(EstadoSolicitud origen, AccionSobreSolicitud accion, Rol actor) {
        Transicion t = TABLA.stream()
            .filter(x -> x.accion() == accion && x.origen() == origen)
            .findFirst()
            .orElseThrow(() -> new TransicionInvalidaException(origen, accion));
        if (!t.roles().contains(actor)) {
            throw new ActorNoPermitidoException(accion);
        }
        return t.destino();
    }

    /** Acciones que el rol puede pedir en ese estado, en el orden del enum (para que la app muestre botones). */
    public static List<AccionSobreSolicitud> accionesDisponibles(EstadoSolicitud estado, Rol actor) {
        return TABLA.stream()
            .filter(t -> t.origen() == estado && t.roles().contains(actor))
            .map(Transicion::accion)
            .sorted(Comparator.naturalOrder())
            .toList();
    }
}
