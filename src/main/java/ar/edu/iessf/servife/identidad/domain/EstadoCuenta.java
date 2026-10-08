package ar.edu.iessf.servife.identidad.domain;

/** Estado de la cuenta (columna estado_cuenta). Una cuenta SUSPENDIDA no puede iniciar sesión (A3). */
public enum EstadoCuenta {
    ACTIVA,
    SUSPENDIDA
}
