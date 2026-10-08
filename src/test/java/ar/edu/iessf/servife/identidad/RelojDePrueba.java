package ar.edu.iessf.servife.identidad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj que arranca en la hora real (el JwtDecoder valida contra el reloj del sistema) y se puede adelantar para probar vencimientos (access y refresh). */
class RelojDePrueba extends Clock {

    private Instant ahora = Instant.now();

    void avanzar(Duration duracion) {
        ahora = ahora.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
