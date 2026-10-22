package ar.edu.iessf.servife.identidad.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.GestorRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;

/**
 * Búsqueda de cuentas en las tres tablas (clientes, prestadores, gestores). Un email puede tener una
 * cuenta de cliente y una de prestador de la misma persona, con la misma contraseña (D43); el de un
 * gestor es único entre las tres.
 */
@Service
public class Cuentas {

    private final ClienteRepository clientes;
    private final PrestadorRepository prestadores;
    private final GestorRepository gestores;

    public Cuentas(ClienteRepository clientes, PrestadorRepository prestadores, GestorRepository gestores) {
        this.clientes = clientes;
        this.prestadores = prestadores;
        this.gestores = gestores;
    }

    /** Las cuentas no dadas de baja con ese email (normalizado), en el orden cliente, prestador, gestor. */
    public List<Cuenta> buscarTodasPorEmail(String email) {
        String normalizado = normalizar(email);
        return Stream.of(
                clientes.findByEmailAndEliminadoEnIsNull(normalizado).map(Cuenta.class::cast),
                prestadores.findByEmailAndEliminadoEnIsNull(normalizado).map(Cuenta.class::cast),
                gestores.findByEmailAndEliminadoEnIsNull(normalizado).map(Cuenta.class::cast))
            .flatMap(Optional::stream)
            .toList();
    }

    /** La cuenta de ese rol con ese email, aunque esté dada de baja (el email no se libera). */
    public Optional<Cuenta> buscarPorEmailConBajas(String email, Rol rol) {
        String normalizado = normalizar(email);
        return switch (rol) {
            case CLIENTE -> clientes.findByEmail(normalizado).map(Cuenta.class::cast);
            case PRESTADOR -> prestadores.findByEmail(normalizado).map(Cuenta.class::cast);
            case GESTOR -> gestores.findByEmail(normalizado).map(Cuenta.class::cast);
        };
    }

    /** Busca por uuid en la tabla que corresponde al rol, ignorando las cuentas dadas de baja. */
    public Optional<Cuenta> buscarPorUuid(UUID uuid, Rol rol) {
        return switch (rol) {
            case CLIENTE -> clientes.findByUuidAndEliminadoEnIsNull(uuid).map(Cuenta.class::cast);
            case PRESTADOR -> prestadores.findByUuidAndEliminadoEnIsNull(uuid).map(Cuenta.class::cast);
            case GESTOR -> gestores.findByUuidAndEliminadoEnIsNull(uuid).map(Cuenta.class::cast);
        };
    }

    /** Si ya existe una cuenta con ese email en alguna de las tres tablas; cuenta también las dadas de baja. */
    public boolean emailRegistrado(String email) {
        String normalizado = normalizar(email);
        return clientes.existsByEmail(normalizado)
            || prestadores.existsByEmail(normalizado)
            || gestores.existsByEmail(normalizado);
    }

    public static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
