package ar.edu.iessf.servife.common.archivos;

/**
 * Reconoce JPEG, PNG y WEBP por la firma de los primeros bytes, nunca por la extensión ni por el
 * Content-Type que declara el cliente. La usan D7 (imágenes) y E7 (documentos de validación).
 */
public final class FirmaDeImagen {

    private FirmaDeImagen() {
    }

    /** Devuelve el mime según la firma, o null si no es JPEG, PNG ni WEBP. */
    public static String mime(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
