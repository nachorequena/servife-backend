package ar.edu.iessf.servife.catalogo.dto;

import java.util.List;

/** Días de la semana en que trabaja el prestador, ordenados (1 = lunes ... 7 = domingo). */
public record DisponibilidadResponse(List<Integer> dias) {
}
