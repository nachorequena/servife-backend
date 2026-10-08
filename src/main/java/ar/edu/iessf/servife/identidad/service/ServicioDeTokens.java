package ar.edu.iessf.servife.identidad.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.config.JwtConfig;
import ar.edu.iessf.servife.config.JwtPropiedades;
import ar.edu.iessf.servife.identidad.domain.RefreshToken;
import ar.edu.iessf.servife.identidad.dto.TokensResponse;
import ar.edu.iessf.servife.identidad.repository.RefreshTokenRepository;

/**
 * Emite y revoca los tokens de sesión: access JWT (HS256, 15 min) y refresh opaco (32 bytes
 * aleatorios en Base64 URL, 7 días) del que solo se guarda el SHA-256. No registra tokens en el log.
 */
@Service
public class ServicioDeTokens {

    private static final int BYTES_DEL_REFRESH = 32;

    private final SecureRandom azar = new SecureRandom();
    private final RefreshTokenRepository refreshTokens;
    private final JwtEncoder jwtEncoder;
    private final JwtPropiedades propiedades;
    private final Clock reloj;

    public ServicioDeTokens(RefreshTokenRepository refreshTokens, JwtEncoder jwtEncoder, JwtPropiedades propiedades,
            Clock reloj) {
        this.refreshTokens = refreshTokens;
        this.jwtEncoder = jwtEncoder;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** Access JWT con sub = uuid y rol, y un refresh nuevo (guardado solo como hash). */
    @Transactional
    public TokensResponse emitir(UUID uuid, Rol rol) {
        Instant ahora = reloj.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(uuid.toString())
            .claim(JwtConfig.CLAIM_ROL, rol.name())
            .issuedAt(ahora)
            .expiresAt(ahora.plus(propiedades.duracionAccess()))
            .build();
        String access = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();

        String refresh = nuevoRefresh();
        refreshTokens.save(new RefreshToken(Hashes.sha256Hex(refresh), uuid, rol, ahora,
            ahora.plus(propiedades.duracionRefresh())));
        return new TokensResponse(access, refresh, rol);
    }

    /**
     * Valida el refresh y lo revoca en el mismo paso, así un segundo uso falla. Da 401
     * REFRESH_INVALIDO si no existe, venció o ya fue revocado.
     */
    @Transactional
    public RefreshToken consumir(String refreshToken) {
        String hash = Hashes.sha256Hex(refreshToken);
        RefreshToken guardado = refreshTokens.findByTokenHash(hash).orElseThrow(ServicioDeTokens::refreshInvalido);
        if (refreshTokens.revocarSiVigente(hash, reloj.instant()) == 0) {
            throw refreshInvalido();
        }
        return guardado;
    }

    /** Revoca todos los refresh vigentes del usuario (cambio y recuperación de contraseña). */
    @Transactional
    public void revocarTodos(UUID uuid) {
        refreshTokens.revocarTodosDe(uuid, reloj.instant());
    }

    private String nuevoRefresh() {
        byte[] bytes = new byte[BYTES_DEL_REFRESH];
        azar.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static NegocioException refreshInvalido() {
        return new NegocioException(HttpStatus.UNAUTHORIZED, "REFRESH_INVALIDO", "Tu sesión venció. Ingresá de nuevo.");
    }
}
