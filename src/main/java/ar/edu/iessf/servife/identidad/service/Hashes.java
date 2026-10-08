package ar.edu.iessf.servife.identidad.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 en hexadecimal minúscula (64 caracteres). Lo usan los refresh tokens y los códigos de
 * recuperación: se guarda el hash, nunca el valor en claro.
 */
public final class Hashes {

    private Hashes() {
    }

    public static String sha256Hex(String valor) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", e);
        }
    }
}
