package ar.edu.iessf.servife.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.catalogo.domain.TipoServicio;
import ar.edu.iessf.servife.catalogo.repository.TipoServicioRepository;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cliente;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoCuenta;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Gestor;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.GestorRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;
import ar.edu.iessf.servife.identidad.service.Cuentas;

/**
 * Entidades de usuario y búsqueda de cuentas contra PostgreSQL real: Hibernate valida el
 * mapeo contra V1-V3. Cada test corre en una transacción que se revierte.
 */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class CuentasIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private Cuentas cuentas;
    @Autowired private ClienteRepository clientes;
    @Autowired private PrestadorRepository prestadores;
    @Autowired private GestorRepository gestores;
    @Autowired private TipoServicioRepository tiposServicio;
    @Autowired private JdbcTemplate jdbc;

    private Cliente guardarAna() {
        return clientes.saveAndFlush(new Cliente("Ana Pérez", "ana@mail.com", "hash"));
    }

    @Test
    void buscaPorEmailNormalizandoMayusculasYEspacios() {
        Cliente ana = guardarAna();

        Cuenta encontrada = cuentas.buscarPorEmail(" Ana@Mail.com ").orElseThrow();

        assertThat(encontrada.getRol()).isEqualTo(Rol.CLIENTE);
        assertThat(encontrada.getUuid()).isEqualTo(ana.getUuid());
        assertThat(encontrada.getEstadoCuenta()).isEqualTo(EstadoCuenta.ACTIVA);
    }

    @Test
    void guardaElEmailNormalizado() {
        clientes.saveAndFlush(new Cliente("Ana", " ANA@Mail.com ", "hash"));

        assertThat(clientes.existsByEmail("ana@mail.com")).isTrue();
    }

    @Test
    void emailRegistradoIgnoraMayusculasYAbarcaLasTresTablas() {
        guardarAna();
        gestores.saveAndFlush(new Gestor("Gaby", "gaby@mail.com", "hash"));

        assertThat(cuentas.emailRegistrado("ANA@mail.com")).isTrue();
        assertThat(cuentas.emailRegistrado(" gaby@mail.com")).isTrue();
        assertThat(cuentas.emailRegistrado("otro@mail.com")).isFalse();
    }

    @Test
    void unaCuentaDadaDeBajaNoSeBuscaPeroElEmailSigueRegistrado() {
        Cliente ana = guardarAna();
        ana.darDeBaja();
        clientes.saveAndFlush(ana);

        assertThat(cuentas.buscarPorEmail("ana@mail.com")).isEmpty();
        assertThat(cuentas.buscarPorUuid(ana.getUuid(), Rol.CLIENTE)).isEmpty();
        assertThat(cuentas.emailRegistrado("ANA@mail.com")).isTrue();
    }

    @Test
    void buscaPorUuidSoloEnLaTablaDelRol() {
        Cliente ana = guardarAna();

        assertThat(cuentas.buscarPorUuid(ana.getUuid(), Rol.CLIENTE)).isPresent();
        assertThat(cuentas.buscarPorUuid(ana.getUuid(), Rol.PRESTADOR)).isEmpty();
        assertThat(cuentas.buscarPorUuid(ana.getUuid(), Rol.GESTOR)).isEmpty();
    }

    @Test
    void elPrestadorConTipoGasQuedaPendiente() {
        TipoServicio gas = tiposServicio.findByEliminadoEnIsNullOrderByNombre().stream()
            .filter(t -> t.getNombre().equals("Gas")).findFirst().orElseThrow();
        assertThat(gas.isRequiereMatricula()).isTrue();

        Prestador pedro = prestadores.saveAndFlush(new Prestador("Pedro Gómez", "pedro@mail.com", "hash", gas));

        String estado = jdbc.queryForObject(
            "SELECT estado_validacion FROM prestadores WHERE uuid = ?", String.class, pedro.getUuid());
        assertThat(estado).isEqualTo("PENDIENTE");
        assertThat(pedro.getEstadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);

        Cuenta encontrada = cuentas.buscarPorEmail("PEDRO@mail.com").orElseThrow();
        assertThat(encontrada.getRol()).isEqualTo(Rol.PRESTADOR);
        assertThat(cuentas.buscarPorUuid(pedro.getUuid(), Rol.PRESTADOR)).isPresent();
    }

    @Test
    void elGestorTieneRolGestorYCambiaContrasenia() {
        gestores.saveAndFlush(new Gestor("Gaby", "gaby@mail.com", "hash"));

        Cuenta gestor = cuentas.buscarPorEmail("gaby@mail.com").orElseThrow();
        gestor.cambiarContrasenia("otro-hash");

        assertThat(gestor.getRol()).isEqualTo(Rol.GESTOR);
        assertThat(gestor.getContrasenia()).isEqualTo("otro-hash");
    }

    @Test
    void listaLosTiposDeServicioOrdenadosPorNombre() {
        assertThat(tiposServicio.findByEliminadoEnIsNullOrderByNombre())
            .extracting(TipoServicio::getNombre).hasSize(7).isSorted();
    }
}
