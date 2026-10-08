package ar.edu.iessf.servife.catalogo;

import java.util.Set;

/** Lista fija de íconos (nombres de Ionicons) que el gestor puede asignar a un tipo de servicio (CU13). */
public final class IconosDeServicio {

    public static final Set<String> PERMITIDOS = Set.of(
        "water", "flash", "sparkles", "flame", "hammer", "leaf",
        "construct", "brush", "car", "home", "paw", "laptop");

    private IconosDeServicio() {
    }
}
