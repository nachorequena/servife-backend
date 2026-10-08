package ar.edu.iessf.servife.common.archivos;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Guarda cada archivo como un fichero llamado como su uuid dentro de servife.archivos.directorio
 * (en Docker, un volumen). Crea el directorio al arrancar. Solo acepta nombres uuid: sin separadores
 * ni "..", no hay forma de salir del directorio.
 */
@Component
public class AlmacenEnDisco implements AlmacenDeArchivos {

    private final Path directorio;

    public AlmacenEnDisco(@Value("${servife.archivos.directorio}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directorio);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio de archivos " + this.directorio, e);
        }
    }

    @Override
    public void guardar(String nombre, InputStream datos) {
        Path destino = resolver(nombre);
        try {
            Files.copy(datos, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo " + nombre, e);
        }
    }

    @Override
    public InputStream leer(String nombre) {
        try {
            return Files.newInputStream(resolver(nombre));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo " + nombre, e);
        }
    }

    private Path resolver(String nombre) {
        if (nombre == null || !esUuidCanonico(nombre)) {
            throw new IllegalArgumentException("Nombre de archivo inválido: solo se aceptan uuid");
        }
        Path ruta = directorio.resolve(nombre).normalize();
        if (!ruta.startsWith(directorio)) {
            throw new IllegalArgumentException("Nombre de archivo inválido");
        }
        return ruta;
    }

    private static boolean esUuidCanonico(String nombre) {
        try {
            return UUID.fromString(nombre).toString().equals(nombre);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
