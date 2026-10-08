package ar.edu.iessf.servife.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.dto.ActualizarPerfilDePrestadorRequest;
import ar.edu.iessf.servife.catalogo.dto.DisponibilidadRequest;
import ar.edu.iessf.servife.catalogo.dto.DisponibilidadResponse;
import ar.edu.iessf.servife.catalogo.dto.PerfilDeServicioResponse;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.catalogo.service.ServicioDePerfilDePrestador;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;

/** B7, B8 y C5 (porción 1) contra PostgreSQL real. Cada test corre en una transacción que se revierte. */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ServicioDePerfilDePrestadorTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ServicioDePerfilDePrestador servicio;
    @Autowired private PrestadorRepository prestadores;
    @Autowired private TipoServicioRepository tiposServicio;

    private TipoServicio tipo(int posicion) {
        return tiposServicio.findByEliminadoEnIsNullOrderByNombre().get(posicion);
    }

    private Prestador guardarPrestador(String email, TipoServicio tipo) {
        return prestadores.saveAndFlush(new Prestador("Pedro Gas", email, "hash", tipo));
    }

    private static ActualizarPerfilDePrestadorRequest pedido(UUID tipo, String zona, BigDecimal lat, BigDecimal lng,
            Integer radio, String descripcion) {
        return new ActualizarPerfilDePrestadorRequest(tipo, zona, lat, lng, radio, descripcion);
    }

    @Test
    void guardaLosCamposDelPerfilYRecortaEspacios() {
        Prestador p = guardarPrestador("p1@mail.com", tipo(0));

        PerfilDeServicioResponse r = servicio.actualizarPerfil(p.getUuid(), pedido(tipo(0).getUuid(),
            "  Centro, Santa Fe ", new BigDecimal("-31.633345"), new BigDecimal("-60.705000"), 15, " Gasista matriculado "));

        assertThat(r.zona()).isEqualTo("Centro, Santa Fe");
        assertThat(r.descripcion()).isEqualTo("Gasista matriculado");
        assertThat(r.radioKm()).isEqualTo(15);
        assertThat(r.lat()).isEqualByComparingTo("-31.63");
        assertThat(r.lng()).isEqualByComparingTo("-60.71");
        assertThat(r.tipoServicio().uuid()).isEqualTo(tipo(0).getUuid());
        assertThat(servicio.obtenerPerfil(p.getUuid()).zona()).isEqualTo("Centro, Santa Fe");
    }

    @Test
    void textosEnBlancoSeGuardanComoNulo() {
        Prestador p = guardarPrestador("p2@mail.com", tipo(0));

        PerfilDeServicioResponse r = servicio.actualizarPerfil(p.getUuid(),
            pedido(tipo(0).getUuid(), "   ", null, null, null, ""));

        assertThat(r.zona()).isNull();
        assertThat(r.descripcion()).isNull();
        assertThat(r.lat()).isNull();
    }

    @Test
    void cambiarElTipoDeUnAprobadoLoVuelveAPendiente() {
        Prestador p = guardarPrestador("p3@mail.com", tipo(0));
        p.setEstadoValidacion(EstadoValidacion.APROBADO);
        prestadores.saveAndFlush(p);

        PerfilDeServicioResponse r = servicio.actualizarPerfil(p.getUuid(),
            pedido(tipo(1).getUuid(), null, null, null, null, null));

        assertThat(r.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
        assertThat(r.idTipoServicio()).isEqualTo(tipo(1).getUuid());
    }

    @Test
    void cambiarElTipoDeUnRechazadoLoVuelveAPendiente() {
        Prestador p = guardarPrestador("p4@mail.com", tipo(0));
        p.setEstadoValidacion(EstadoValidacion.RECHAZADO);
        prestadores.saveAndFlush(p);

        assertThat(servicio.actualizarPerfil(p.getUuid(), pedido(tipo(1).getUuid(), null, null, null, null, null))
            .estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
    }

    @Test
    void elMismoTipoNoCambiaElEstado() {
        Prestador p = guardarPrestador("p5@mail.com", tipo(0));
        p.setEstadoValidacion(EstadoValidacion.APROBADO);
        prestadores.saveAndFlush(p);

        PerfilDeServicioResponse r = servicio.actualizarPerfil(p.getUuid(),
            pedido(tipo(0).getUuid(), "Zona", null, null, 10, null));

        assertThat(r.estadoValidacion()).isEqualTo(EstadoValidacion.APROBADO);
    }

    @Test
    void tipoInexistenteODadoDeBajaEsValidacionDelCampoIdTipoServicio() {
        Prestador p = guardarPrestador("p6@mail.com", tipo(0));
        TipoServicio baja = tipo(1);
        baja.darDeBaja();
        tiposServicio.saveAndFlush(baja);

        assertThatThrownBy(() -> servicio.actualizarPerfil(p.getUuid(),
            pedido(UUID.randomUUID(), null, null, null, null, null)))
            .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getCampo()).isEqualTo("idTipoServicio"));
        assertThatThrownBy(() -> servicio.actualizarPerfil(p.getUuid(),
            pedido(baja.getUuid(), null, null, null, null, null)))
            .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getCampo()).isEqualTo("idTipoServicio"));
    }

    @Test
    void latSinLngOLngSinLatEsValidacionDelCampoLat() {
        Prestador p = guardarPrestador("p7@mail.com", tipo(0));

        assertThatThrownBy(() -> servicio.actualizarPerfil(p.getUuid(),
            pedido(tipo(0).getUuid(), null, new BigDecimal("-31.6"), null, null, null)))
            .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getCampo()).isEqualTo("lat"));
        assertThatThrownBy(() -> servicio.actualizarPerfil(p.getUuid(),
            pedido(tipo(0).getUuid(), null, null, new BigDecimal("-60.7"), null, null)))
            .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getCampo()).isEqualTo("lat"));
    }

    @Test
    void prestadorInexistenteEsNoEncontrado() {
        assertThatThrownBy(() -> servicio.obtenerPerfil(UUID.randomUUID()))
            .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.reemplazarDisponibilidad(UUID.randomUUID(), new DisponibilidadRequest(List.of(1))))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void reemplazaLaDisponibilidadCompleta() {
        Prestador p = guardarPrestador("p8@mail.com", tipo(0));
        servicio.reemplazarDisponibilidad(p.getUuid(), new DisponibilidadRequest(List.of(5, 1, 3)));
        assertThat(servicio.obtenerDisponibilidad(p.getUuid()).dias()).containsExactly(1, 3, 5);

        DisponibilidadResponse r = servicio.reemplazarDisponibilidad(p.getUuid(), new DisponibilidadRequest(List.of(2)));

        assertThat(r.dias()).containsExactly(2);
        assertThat(servicio.obtenerDisponibilidad(p.getUuid()).dias()).containsExactly(2);
    }

    @Test
    void diasRepetidosSeDeduplicanYListaVaciaLimpia() {
        Prestador p = guardarPrestador("p9@mail.com", tipo(0));

        assertThat(servicio.reemplazarDisponibilidad(p.getUuid(), new DisponibilidadRequest(List.of(3, 3, 1, 3))).dias())
            .containsExactly(1, 3);
        assertThat(servicio.reemplazarDisponibilidad(p.getUuid(), new DisponibilidadRequest(List.of())).dias()).isEmpty();
    }

    @Test
    void consultarLaDisponibilidadDeOtroPrestadorFuncionaYInexistenteODadoDeBajaEs404() {
        Prestador p = guardarPrestador("p10@mail.com", tipo(0));
        servicio.reemplazarDisponibilidad(p.getUuid(), new DisponibilidadRequest(List.of(4, 6)));

        assertThat(servicio.obtenerDisponibilidad(p.getUuid()).dias()).containsExactly(4, 6);
        assertThatThrownBy(() -> servicio.obtenerDisponibilidad(UUID.randomUUID()))
            .isInstanceOf(RecursoNoEncontradoException.class);

        p.darDeBaja();
        prestadores.saveAndFlush(p);
        assertThatThrownBy(() -> servicio.obtenerDisponibilidad(p.getUuid()))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
