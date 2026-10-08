package ar.edu.iessf.servife.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.JwtPropiedades;
import ar.edu.iessf.servife.identidad.domain.RefreshToken;
import ar.edu.iessf.servife.identidad.dto.TokensResponse;
import ar.edu.iessf.servife.identidad.repository.RefreshTokenRepository;
import ar.edu.iessf.servife.identidad.service.Hashes;
import ar.edu.iessf.servife.identidad.service.ServicioDeTokens;

/** Emisión, consumo y revocación de tokens contra PostgreSQL real (tabla refresh_tokens de V2). */
@SpringBootTest(properties = "servife.jwt.secreto=secreto-de-prueba-de-al-menos-32-caracteres")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ServicioDeTokensTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private RefreshTokenRepository repositorio;
    @Autowired private JwtEncoder encoder;
    @Autowired private JwtDecoder decoder;
    @Autowired private JwtPropiedades propiedades;
    @Autowired private JdbcTemplate jdbc;

    private RelojDePrueba reloj;
    private ServicioDeTokens tokens;

    @BeforeEach
    void preparar() {
        reloj = new RelojDePrueba();
        tokens = new ServicioDeTokens(repositorio, encoder, propiedades, reloj);
    }

    @Test
    void emitirDevuelveUnAccessConSubYRolYUnRefreshOpaco() {
        UUID uuid = UUID.randomUUID();

        TokensResponse emitidos = tokens.emitir(uuid, Rol.PRESTADOR);

        Jwt jwt = decoder.decode(emitidos.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(uuid.toString());
        assertThat(jwt.<String>getClaim(JwtConfig.CLAIM_ROL)).isEqualTo("PRESTADOR");
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plus(Duration.ofMinutes(15)));
        assertThat(emitidos.rol()).isEqualTo(Rol.PRESTADOR);
        // 32 bytes en Base64 URL sin relleno = 43 caracteres
        assertThat(emitidos.refreshToken()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(emitidos.refreshToken())).hasSize(32);
    }

    @Test
    void elRefreshSeGuardaSoloComoSha256YVenceALos7Dias() {
        UUID uuid = UUID.randomUUID();

        TokensResponse emitidos = tokens.emitir(uuid, Rol.CLIENTE);

        String hash = Hashes.sha256Hex(emitidos.refreshToken());
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        RefreshToken guardado = repositorio.findByTokenHash(hash).orElseThrow();
        assertThat(guardado.getUuidUsuario()).isEqualTo(uuid);
        assertThat(guardado.getRol()).isEqualTo(Rol.CLIENTE);
        assertThat(guardado.getRevocadoEn()).isNull();
        assertThat(guardado.getExpiraEn()).isEqualTo(reloj.instant().plus(Duration.ofDays(7)));
        Integer conElTokenEnClaro = jdbc.queryForObject(
            "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, emitidos.refreshToken());
        assertThat(conElTokenEnClaro).isZero();
    }

    @Test
    void sha256HexDaMinusculasYCoincideConUnValorConocido() {
        assertThat(Hashes.sha256Hex("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void dosEmisionesDanRefreshDistintos() {
        UUID uuid = UUID.randomUUID();

        assertThat(tokens.emitir(uuid, Rol.CLIENTE).refreshToken())
            .isNotEqualTo(tokens.emitir(uuid, Rol.CLIENTE).refreshToken());
    }

    @Test
    void consumirDevuelveElDuenioYRevocaElToken() {
        UUID uuid = UUID.randomUUID();
        TokensResponse emitidos = tokens.emitir(uuid, Rol.GESTOR);

        RefreshToken consumido = tokens.consumir(emitidos.refreshToken());

        assertThat(consumido.getUuidUsuario()).isEqualTo(uuid);
        assertThat(consumido.getRol()).isEqualTo(Rol.GESTOR);
        Integer revocados = jdbc.queryForObject(
            "SELECT count(*) FROM refresh_tokens WHERE token_hash = ? AND revocado_en IS NOT NULL",
            Integer.class, Hashes.sha256Hex(emitidos.refreshToken()));
        assertThat(revocados).isEqualTo(1);
    }

    @Test
    void unTokenConsumidoNoSePuedeUsarOtraVez() {
        TokensResponse emitidos = tokens.emitir(UUID.randomUUID(), Rol.CLIENTE);
        tokens.consumir(emitidos.refreshToken());

        assertRefreshInvalido(emitidos.refreshToken());
    }

    @Test
    void unTokenInexistenteDa401() {
        assertRefreshInvalido("no-existe");
    }

    @Test
    void unTokenVencidoDa401() {
        TokensResponse emitidos = tokens.emitir(UUID.randomUUID(), Rol.CLIENTE);
        reloj.avanzar(Duration.ofDays(8));

        assertRefreshInvalido(emitidos.refreshToken());
    }

    @Test
    void revocarTodosInvalidaSoloLosTokensDeEseUsuario() {
        UUID ana = UUID.randomUUID();
        UUID otro = UUID.randomUUID();
        TokensResponse deAna1 = tokens.emitir(ana, Rol.CLIENTE);
        TokensResponse deAna2 = tokens.emitir(ana, Rol.CLIENTE);
        TokensResponse delOtro = tokens.emitir(otro, Rol.CLIENTE);

        tokens.revocarTodos(ana);

        assertRefreshInvalido(deAna1.refreshToken());
        assertRefreshInvalido(deAna2.refreshToken());
        assertThat(tokens.consumir(delOtro.refreshToken()).getUuidUsuario()).isEqualTo(otro);
    }

    private void assertRefreshInvalido(String refreshToken) {
        assertThatThrownBy(() -> tokens.consumir(refreshToken))
            .isInstanceOfSatisfying(NegocioException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(e.getCodigo()).isEqualTo("REFRESH_INVALIDO");
                assertThat(e.getMessage()).isEqualTo("Tu sesión venció. Ingresá de nuevo.");
            });
    }
}
