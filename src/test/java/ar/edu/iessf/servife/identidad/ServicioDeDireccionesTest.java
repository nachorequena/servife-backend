package ar.edu.iessf.servife.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.dto.ActualizarUsuarioRequest;
import ar.edu.iessf.servife.identidad.dto.DireccionRequest;
import ar.edu.iessf.servife.identidad.dto.DireccionResponse;
import ar.edu.iessf.servife.identidad.mapper.UsuarioMapper;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.service.Cuentas;
import ar.edu.iessf.servife.identidad.service.ServicioDeCuenta;
import ar.edu.iessf.servife.identidad.service.ServicioDeDirecciones;
import ar.edu.iessf.servife.identidad.service.ServicioDeTokens;

/** A10 a A14 y la sincronización con A6, contra PostgreSQL real (incluye el índice de una sola principal). */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ServicioDeDireccionesTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ServicioDeDirecciones servicio;
    @Autowired private ClienteRepository clientes;
    @Autowired private Cuentas cuentas;
    @Autowired private UsuarioMapper mapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private Cliente ana;

    @BeforeEach
    void preparar() {
        ana = clientes.saveAndFlush(new Cliente("Ana Pérez", UUID.randomUUID() + "@mail.com", "hash"));
    }

    private DireccionResponse crear(String alias, String direccion, Boolean principal) {
        return servicio.crear(ana.getUuid(), new DireccionRequest(alias, direccion, principal));
    }

    private DireccionResponse principal() {
        return servicio.listar(ana.getUuid()).stream().filter(DireccionResponse::principal).findFirst().orElseThrow();
    }

    @Test
    void laPrimeraEsPrincipalYSeCopiaEnElPerfil() {
        DireccionResponse casa = crear("  Casa ", " San Martín 123 ", null);
        DireccionResponse trabajo = crear("Trabajo", "Rivadavia 500", false);

        assertThat(casa.principal()).isTrue();
        assertThat(casa.alias()).isEqualTo("Casa");
        assertThat(trabajo.principal()).isFalse();
        assertThat(servicio.listar(ana.getUuid())).extracting(DireccionResponse::uuid)
            .containsExactly(casa.uuid(), trabajo.uuid());
        assertThat(ana.getDireccion()).isEqualTo("San Martín 123");
    }

    @Test
    void marcarOtraComoPrincipalDesmarcaLaAnterior() {
        crear("Casa", "San Martín 123", null);
        DireccionResponse trabajo = crear("Trabajo", "Rivadavia 500", null);

        servicio.marcarPrincipal(ana.getUuid(), trabajo.uuid());

        assertThat(servicio.listar(ana.getUuid())).filteredOn(DireccionResponse::principal)
            .extracting(DireccionResponse::uuid).containsExactly(trabajo.uuid());
        assertThat(ana.getDireccion()).isEqualTo("Rivadavia 500");

        DireccionResponse quinta = crear("Quinta", "Ruta 1 km 5", true);
        assertThat(principal().uuid()).isEqualTo(quinta.uuid());
    }

    @Test
    void editarLaPrincipalActualizaElPerfil() {
        DireccionResponse casa = crear("Casa", "San Martín 123", null);

        servicio.actualizar(ana.getUuid(), casa.uuid(), new DireccionRequest("Casa", "San Martín 999", null));

        assertThat(ana.getDireccion()).isEqualTo("San Martín 999");
    }

    @Test
    void borrarLaPrincipalPromueveOtraYBorrarLaUltimaVaciaElPerfil() {
        DireccionResponse casa = crear("Casa", "San Martín 123", null);
        DireccionResponse trabajo = crear("Trabajo", "Rivadavia 500", null);

        servicio.eliminar(ana.getUuid(), casa.uuid());

        assertThat(servicio.listar(ana.getUuid())).singleElement().satisfies(d -> {
            assertThat(d.uuid()).isEqualTo(trabajo.uuid());
            assertThat(d.principal()).isTrue();
        });
        assertThat(ana.getDireccion()).isEqualTo("Rivadavia 500");

        servicio.eliminar(ana.getUuid(), trabajo.uuid());

        assertThat(servicio.listar(ana.getUuid())).isEmpty();
        assertThat(ana.getDireccion()).isNull();
    }

    @Test
    void laDireccionDeOtroClienteEs404() {
        DireccionResponse casa = crear("Casa", "San Martín 123", null);
        Cliente beto = clientes.saveAndFlush(new Cliente("Beto", UUID.randomUUID() + "@mail.com", "hash"));

        assertThatThrownBy(() -> servicio.eliminar(beto.getUuid(), casa.uuid()))
            .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.marcarPrincipal(beto.getUuid(), casa.uuid()))
            .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(servicio.listar(beto.getUuid())).isEmpty();
    }

    @Test
    void a6EditaLaPrincipalOLaCreaYEnBlancoLaBorra() {
        ServicioDeCuenta cuenta = new ServicioDeCuenta(cuentas, mapper, passwordEncoder, mock(ServicioDeTokens.class), servicio);

        cuenta.actualizar(ana.getUuid(), Rol.CLIENTE, new ActualizarUsuarioRequest("Ana Pérez", null, "San Martín 123", null));
        assertThat(servicio.listar(ana.getUuid())).singleElement().satisfies(d -> {
            assertThat(d.alias()).isEqualTo("Casa");
            assertThat(d.principal()).isTrue();
        });

        crear("Trabajo", "Rivadavia 500", null);
        cuenta.actualizar(ana.getUuid(), Rol.CLIENTE, new ActualizarUsuarioRequest("Ana Pérez", null, "San Martín 999", null));
        assertThat(principal().direccion()).isEqualTo("San Martín 999");
        assertThat(servicio.listar(ana.getUuid())).hasSize(2);

        cuenta.actualizar(ana.getUuid(), Rol.CLIENTE, new ActualizarUsuarioRequest("Ana Pérez", null, " ", null));
        List<DireccionResponse> quedan = servicio.listar(ana.getUuid());
        assertThat(quedan).singleElement().extracting(DireccionResponse::direccion).isEqualTo("Rivadavia 500");
        assertThat(ana.getDireccion()).isEqualTo("Rivadavia 500");
    }
}
