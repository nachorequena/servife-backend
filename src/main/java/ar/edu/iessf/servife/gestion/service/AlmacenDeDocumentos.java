package ar.edu.iessf.servife.gestion.service;

import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ar.edu.iessf.servife.common.archivos.AlmacenEnDisco;

/**
 * Dónde viven los bytes de los documentos de validación (D01): un AlmacenEnDisco sobre su propia carpeta
 * (servife.documentos.directorio, DOCUMENTOS_DIR), separada de la de las imágenes. No es un
 * AlmacenDeArchivos a propósito, para que nada de /archivos pueda recibirlo por inyección.
 */
@Component
public class AlmacenDeDocumentos {

    private final AlmacenEnDisco disco;

    public AlmacenDeDocumentos(@Value("${servife.documentos.directorio}") String directorio) {
        this.disco = new AlmacenEnDisco(directorio);
    }

    public void guardar(String nombre, InputStream datos) {
        disco.guardar(nombre, datos);
    }

    public void borrar(String nombre) {
        disco.borrar(nombre);
    }

    public InputStream leer(String nombre) {
        return disco.leer(nombre);
    }
}
