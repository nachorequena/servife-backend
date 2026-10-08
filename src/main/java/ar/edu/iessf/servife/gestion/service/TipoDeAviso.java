package ar.edu.iessf.servife.gestion.service;

/** Motivo de un aviso dentro de la app; la app lo usa para elegir ícono y a dónde navegar. */
public enum TipoDeAviso {
    PERFIL_APROBADO,
    PERFIL_RECHAZADO,
    SOLICITUD_NUEVA,
    SOLICITUD_ACEPTADA,
    SOLICITUD_RECHAZADA,
    SOLICITUD_EN_CURSO,
    SOLICITUD_FINALIZADA,
    SOLICITUD_CANCELADA
}
