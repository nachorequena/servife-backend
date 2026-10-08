package ar.edu.iessf.servife.solicitudes.domain;

import ar.edu.iessf.servife.common.seguridad.Rol;

/** Deja una solicitud en un estado cualquiera para armar escenarios (en producción el estado solo cambia por C4). */
public final class SolicitudDePrueba {

    private SolicitudDePrueba() {
    }

    public static Solicitud enEstado(Solicitud s, EstadoSolicitud estado) {
        s.restaurar(estado, null, null, null);
        return s;
    }

    public static Solicitud enEstado(Solicitud s, EstadoSolicitud estado, String motivo, Rol canceladaPor, Long precio) {
        s.restaurar(estado, motivo, canceladaPor, precio);
        return s;
    }
}
