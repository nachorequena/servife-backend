package ar.edu.iessf.servife.common.archivos;

import java.io.InputStream;

/**
 * Dónde viven los bytes de los archivos subidos. El nombre es siempre el uuid del archivo
 * (nunca uno del cliente); las implementaciones rechazan cualquier otro nombre.
 */
public interface AlmacenDeArchivos {

    /** Guarda los datos bajo ese nombre. Lanza IllegalArgumentException si no es un uuid. */
    void guardar(String nombre, InputStream datos);

    /** Abre los datos guardados; el que llama cierra el stream. */
    InputStream leer(String nombre);
}
