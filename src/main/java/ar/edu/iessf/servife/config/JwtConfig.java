package ar.edu.iessf.servife.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Codificador y decodificador de JWT firmados con HS256.
 * La emisión del token (login, refresh) es del módulo A: usa el JwtEncoder de acá,
 * con claims sub = uuid del usuario y rol = CLIENTE | PRESTADOR | GESTOR.
 * Al firmar, el header tiene que declarar HS256 (JwsHeader.with(MacAlgorithm.HS256)).
 */
@Configuration
@EnableConfigurationProperties(JwtPropiedades.class)
public class JwtConfig {

    public static final String CLAIM_ROL = "rol";

    private final SecretKey clave;

    public JwtConfig(JwtPropiedades propiedades) {
        this.clave = new SecretKeySpec(propiedades.secreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
