package ar.edu.iessf.servife.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

/** Lo que el módulo A va a usar para emitir tokens: firma HS256 y rol → ROLE_<rol>. */
class JwtConfigTest {

    private final JwtConfig config = new JwtConfig(
        new JwtPropiedades("secreto-de-prueba-de-al-menos-32-caracteres", Duration.ofMinutes(15), Duration.ofDays(7)));

    @Test
    void unTokenFirmadoSeDecodificaYSuRolSeConvierteEnAutoridad() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject("7f1c2d4e-0000-4000-8000-000000000001")
            .claim(JwtConfig.CLAIM_ROL, "PRESTADOR")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
            .build();
        String token = config.jwtEncoder()
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();

        Jwt jwt = config.jwtDecoder().decode(token);
        var autoridades = new SeguridadConfig().convertidorDeRol().convert(jwt).getAuthorities();

        assertThat(jwt.getSubject()).isEqualTo("7f1c2d4e-0000-4000-8000-000000000001");
        assertThat(autoridades).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_PRESTADOR");
    }

    @Test
    void unSecretoCortoNoArranca() {
        assertThatThrownBy(() -> new JwtPropiedades("corto", Duration.ofMinutes(15), Duration.ofDays(7)))
            .isInstanceOf(IllegalStateException.class);
    }
}
