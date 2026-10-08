package ar.edu.iessf.servife.common.archivos;

/** El almacén no tiene los bytes de un archivo que sí existe en la base. */
public class ArchivoFaltanteException extends RuntimeException {

    public ArchivoFaltanteException(String nombre, Throwable causa) {
        super("Falta en el almacén el archivo " + nombre, causa);
    }
}
