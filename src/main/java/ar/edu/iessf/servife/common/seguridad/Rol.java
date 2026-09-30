package ar.edu.iessf.servife.common.seguridad;

/** Viaja en el claim "rol" del JWT. Cada uno se mapea a la autoridad ROLE_<nombre>. */
public enum Rol {
    CLIENTE,
    PRESTADOR,
    GESTOR
}
