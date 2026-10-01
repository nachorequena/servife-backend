package ar.edu.iessf.servife.identidad.service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ar.edu.iessf.servife.common.seguridad.Rol;
import ar.edu.iessf.servife.identidad.domain.Cuenta;
import ar.edu.iessf.servife.identidad.repository.ClienteRepository;
import ar.edu.iessf.servife.identidad.repository.GestorRepository;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;

/**
 * Búsqueda de cuentas en las tres tablas (clientes, prestadores, gestores). El email es único
 * entre las tres, por eso se busca en ese orden y se devuelve la primera coincidencia.
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

    /** Busca por email (normalizado) ignorando las cuentas dadas de baja. */
    public Optional<Cuenta> buscarPorEmail(String email) {
        String normalizado = normalizar(email);
        Optional<Cuenta> cuenta = clientes.findByEmailAndEliminadoEnIsNull(normalizado).map(Cuenta.class::cast);
        if (cuenta.isEmpty()) {
            cuenta = prestadores.findByEmailAndEliminadoEnIsNull(normalizado).map(Cuenta.class::cast);
        }
        if (cuenta.isEmpty()) {
            cuenta = gestores.findByEmailAndEliminadoEnIsNull(normalizado).map(Cuenta.class::cast);
        }
        return cuenta;
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
