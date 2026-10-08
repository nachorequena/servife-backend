package ar.edu.iessf.servife.solicitudes;

import static ar.edu.iessf.servife.common.seguridad.Rol.CLIENTE;
import static ar.edu.iessf.servife.common.seguridad.Rol.GESTOR;
import static ar.edu.iessf.servife.common.seguridad.Rol.PRESTADOR;
import static ar.edu.iessf.servife.solicitudes.domain.AccionSobreSolicitud.ACEPTAR;
import static ar.edu.iessf.servife.solicitudes.domain.AccionSobreSolicitud.CANCELAR;
import static ar.edu.iessf.servife.solicitudes.domain.AccionSobreSolicitud.FINALIZAR;
import static ar.edu.iessf.servife.solicitudes.domain.AccionSobreSolicitud.INICIAR;
import static ar.edu.iessf.servife.solicitudes.domain.AccionSobreSolicitud.RECHAZAR;
import static ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud.ACEPTADA;
import static ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud.CANCELADA;
import static ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud.EN_CURSO;
import static ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud.FINALIZADA;
import static ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud.PENDIENTE;
import static ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud.RECHAZADA;
import static ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud.VALORADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.solicitudes.domain.AccionSobreSolicitud;
import ar.edu.iessf.servife.solicitudes.domain.ActorNoPermitidoException;
import ar.edu.iessf.servife.solicitudes.domain.EstadoSolicitud;
import ar.edu.iessf.servife.solicitudes.domain.MaquinaDeEstados;
import ar.edu.iessf.servife.solicitudes.domain.TransicionInvalidaException;

/** Tabla de transiciones de 02-context, sin Spring. */
class MaquinaDeEstadosTest {

    /** accion, origen, actor, destino: las transiciones válidas (CANCELAR desde ACEPTADA va por rol). */
    private static final List<Object[]> VALIDAS = List.of(
        new Object[] {ACEPTAR, PENDIENTE, PRESTADOR, ACEPTADA},
        new Object[] {RECHAZAR, PENDIENTE, PRESTADOR, RECHAZADA},
        new Object[] {CANCELAR, PENDIENTE, CLIENTE, CANCELADA},
        new Object[] {CANCELAR, ACEPTADA, CLIENTE, CANCELADA},
        new Object[] {CANCELAR, ACEPTADA, PRESTADOR, CANCELADA},
        new Object[] {INICIAR, ACEPTADA, PRESTADOR, EN_CURSO},
        new Object[] {FINALIZAR, EN_CURSO, PRESTADOR, FINALIZADA});

    static Stream<Arguments> transicionesValidas() {
        return VALIDAS.stream().map(v -> Arguments.of(v));
    }

    private static boolean existe(AccionSobreSolicitud accion, EstadoSolicitud origen) {
        return VALIDAS.stream().anyMatch(v -> v[0] == accion && v[1] == origen);
    }

    static Stream<Arguments> paresInexistentes() {
        return Stream.of(AccionSobreSolicitud.values())
            .flatMap(a -> Stream.of(EstadoSolicitud.values()).filter(e -> !existe(a, e)).map(e -> Arguments.of(a, e)));
    }

    /** Para cada par (accion, origen) que existe, los roles que no figuran en la tabla. */
    static Stream<Arguments> actoresNoPermitidos() {
        return Stream.of(AccionSobreSolicitud.values()).flatMap(a -> Stream.of(EstadoSolicitud.values())
            .filter(e -> existe(a, e))
            .flatMap(e -> Stream.of(Rol.values())
                .filter(r -> VALIDAS.stream().noneMatch(v -> v[0] == a && v[1] == e && v[2] == r))
                .map(r -> Arguments.of(a, e, r))));
    }

    @ParameterizedTest(name = "{0} {1} por {2} -> {3}")
    @MethodSource("transicionesValidas")
    void aplicaCadaTransicionValida(AccionSobreSolicitud accion, EstadoSolicitud origen, Rol actor,
            EstadoSolicitud destino) {
        assertThat(MaquinaDeEstados.destino(origen, accion, actor)).isEqualTo(destino);
    }

    @ParameterizedTest(name = "{0} sobre {1}")
    @MethodSource("paresInexistentes")
    void rechazaCadaParInexistenteConTransicionInvalida(AccionSobreSolicitud accion, EstadoSolicitud origen) {
        for (Rol rol : Rol.values()) {
            assertThatThrownBy(() -> MaquinaDeEstados.destino(origen, accion, rol))
                .isInstanceOf(TransicionInvalidaException.class)
                .satisfies(e -> {
                    NegocioException n = (NegocioException) e;
                    assertThat(n.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(n.getCodigo()).isEqualTo("TRANSICION_INVALIDA");
                });
        }
    }

    @ParameterizedTest(name = "{0} sobre {1} por {2}")
    @MethodSource("actoresNoPermitidos")
    void rechazaElRolEquivocado(AccionSobreSolicitud accion, EstadoSolicitud origen, Rol actor) {
        assertThatThrownBy(() -> MaquinaDeEstados.destino(origen, accion, actor))
            .isInstanceOf(ActorNoPermitidoException.class)
            .satisfies(e -> {
                NegocioException n = (NegocioException) e;
                assertThat(n.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(n.getCodigo()).isEqualTo("ACCESO_DENEGADO");
            });
    }

    @Test
    void elMensajeDeTransicionInvalidaNombraLaAccionYElEstado() {
        assertThatThrownBy(() -> MaquinaDeEstados.destino(CANCELADA, ACEPTAR, PRESTADOR))
            .hasMessage("No se puede aceptar una solicitud cancelada.");
        assertThatThrownBy(() -> MaquinaDeEstados.destino(EN_CURSO, CANCELAR, CLIENTE))
            .hasMessage("No se puede cancelar una solicitud en curso.");
    }

    @Test
    void valoradaNoAdmiteNingunaAccion() {
        for (AccionSobreSolicitud accion : AccionSobreSolicitud.values()) {
            assertThatThrownBy(() -> MaquinaDeEstados.destino(VALORADA, accion, CLIENTE))
                .isInstanceOf(TransicionInvalidaException.class);
        }
    }

    static Stream<Arguments> accionesPorEstadoYRol() {
        return Stream.of(
            Arguments.of(PENDIENTE, CLIENTE, List.of(CANCELAR)),
            Arguments.of(PENDIENTE, PRESTADOR, List.of(ACEPTAR, RECHAZAR)),
            Arguments.of(PENDIENTE, GESTOR, List.of()),
            Arguments.of(ACEPTADA, CLIENTE, List.of(CANCELAR)),
            Arguments.of(ACEPTADA, PRESTADOR, List.of(INICIAR, CANCELAR)),
            Arguments.of(ACEPTADA, GESTOR, List.of()),
            Arguments.of(EN_CURSO, CLIENTE, List.of()),
            Arguments.of(EN_CURSO, PRESTADOR, List.of(FINALIZAR)),
            Arguments.of(RECHAZADA, PRESTADOR, List.of()),
            Arguments.of(CANCELADA, CLIENTE, List.of()),
            Arguments.of(FINALIZADA, CLIENTE, List.of()),
            Arguments.of(VALORADA, PRESTADOR, List.of()));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("accionesPorEstadoYRol")
    void accionesDisponiblesSegunEstadoYRol(EstadoSolicitud estado, Rol rol, List<AccionSobreSolicitud> esperadas) {
        assertThat(MaquinaDeEstados.accionesDisponibles(estado, rol)).containsExactlyElementsOf(esperadas);
    }
}
