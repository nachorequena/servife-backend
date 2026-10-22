package ar.edu.iessf.servife.gestion.domain;

/** Tipos de documento de validación (D01). Sin antecedentes penales: solo la declaración jurada. */
public enum TipoDeDocumento {
    DNI_FRENTE,
    DNI_DORSO,
    CERTIFICACION;

    public boolean esDni() {
        return this != CERTIFICACION;
    }
}
