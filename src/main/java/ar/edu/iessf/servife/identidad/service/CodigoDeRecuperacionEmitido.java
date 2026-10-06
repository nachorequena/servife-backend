package ar.edu.iessf.servife.identidad.service;

/** Evento: se guardó un código de recuperación y hay que enviarlo por correo (después del commit). */
public record CodigoDeRecuperacionEmitido(String email, String codigo, long vigenciaEnMinutos) {

    /** El código no debe aparecer en logs ni en trazas. */
    @Override
    public String toString() {
        return "CodigoDeRecuperacionEmitido[email=" + email + "]";
    }
}
