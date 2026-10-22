package ar.edu.iessf.servife.gestion.service;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ar.edu.iessf.servife.common.archivos.ArchivoFaltanteException;
import ar.edu.iessf.servife.common.archivos.FirmaDeImagen;
import ar.edu.iessf.servife.common.error.ConflictoException;
import ar.edu.iessf.servife.common.error.NegocioException;
import ar.edu.iessf.servife.common.error.RecursoNoEncontradoException;
import ar.edu.iessf.servife.common.error.ValidacionException;
import ar.edu.iessf.servife.gestion.domain.DocumentoValidacion;
import ar.edu.iessf.servife.gestion.domain.TipoDeDocumento;
import ar.edu.iessf.servife.gestion.dto.DocumentoResponse;
import ar.edu.iessf.servife.gestion.dto.ExpedienteResponse;
import ar.edu.iessf.servife.gestion.repository.DocumentoValidacionRepository;
import ar.edu.iessf.servife.identidad.domain.EstadoValidacion;
import ar.edu.iessf.servife.identidad.domain.Prestador;
import ar.edu.iessf.servife.identidad.repository.PrestadorRepository;

/**
 * Expediente de validación del prestador (D01, CU14): carga (E7), lectura de metadatos (E7b, E8) y de
 * bytes (E8b, solo gestor), lo que E6 exige para aprobar y el cierre del expediente al decidir. Los
 * archivos son datos personales (Ley 25.326): viven en AlmacenDeDocumentos, nunca en /archivos, y se
 * borran del disco al resolver el expediente.
 */
@Service
public class ServicioDeDocumentos {

    private static final Logger log = LoggerFactory.getLogger(ServicioDeDocumentos.class);

    static final long MAXIMO_BYTES = 5L * 1024 * 1024;
    static final int MAXIMO_CERTIFICACIONES = 5;

    private final DocumentoValidacionRepository documentos;
    private final PrestadorRepository prestadores;
    private final AlmacenDeDocumentos almacen;
    private final Clock reloj;

    public ServicioDeDocumentos(DocumentoValidacionRepository documentos, PrestadorRepository prestadores,
            AlmacenDeDocumentos almacen, Clock reloj) {
        this.documentos = documentos;
        this.prestadores = prestadores;
        this.almacen = almacen;
        this.reloj = reloj;
    }

    /**
     * E7: carga un documento del prestador autenticado. Exige la declaración jurada en cada carga (la
     * primera vez guarda la fecha). Solo mientras está PENDIENTE; un DNI nuevo reemplaza al anterior y las
     * certificaciones llegan hasta MAXIMO_CERTIFICACIONES. Bloquea la fila del prestador para no cruzarse con E6.
     */
    @Transactional
    public DocumentoResponse subir(UUID uuidPrestador, TipoDeDocumento tipo, boolean declaracionJurada, byte[] datos) {
        if (!declaracionJurada) {
            throw new ValidacionException("declaracionJurada", "tenés que aceptar la declaración jurada");
        }
        if (datos.length == 0) {
            throw new ValidacionException("archivo", "está vacío");
        }
        if (datos.length > MAXIMO_BYTES) {
            throw new NegocioException(HttpStatus.PAYLOAD_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE",
                "El archivo supera el tamaño máximo.");
        }
        String mime = FirmaDeImagen.mime(datos);
        if (mime == null) {
            throw new ValidacionException("archivo", "tiene que ser una imagen JPEG, PNG o WEBP");
        }
        Prestador prestador = prestadores.bloquearPorUuid(uuidPrestador)
            .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos al prestador."));
        if (prestador.getEstadoValidacion() != EstadoValidacion.PENDIENTE) {
            throw new ConflictoException("EXPEDIENTE_CERRADO", "Tu perfil ya fue revisado: no se pueden cargar documentos.");
        }

        List<DocumentoValidacion> anteriores = documentos.findByIdPrestadorAndTipoAndEliminadoEnIsNull(prestador.getId(), tipo);
        if (tipo.esDni()) {
            anteriores.forEach(this::darDeBaja);
            documentos.flush();
        } else if (anteriores.size() >= MAXIMO_CERTIFICACIONES) {
            throw new ConflictoException("LIMITE_DE_CERTIFICACIONES", "Ya cargaste " + MAXIMO_CERTIFICACIONES + " certificaciones.");
        }
        prestador.aceptarDeclaracionJurada(reloj.instant());

        // Dos cargas del mismo prestador no se cruzan (fila bloqueada); el índice uq_documentos_dni_vigente es la red.
        DocumentoValidacion documento = documentos.saveAndFlush(new DocumentoValidacion(prestador.getId(), tipo, mime, datos.length));
        String nombre = documento.getRuta();
        almacen.guardar(nombre, new ByteArrayInputStream(datos));
        alTerminar(estado -> {
            if (estado == TransactionSynchronization.STATUS_ROLLED_BACK) {
                almacen.borrar(nombre);
            }
        });
        return aRespuesta(documento);
    }

    /** E7b: el expediente del prestador autenticado, sin bytes. */
    @Transactional(readOnly = true)
    public ExpedienteResponse expedientePropio(UUID uuidPrestador) {
        return expediente(buscar(uuidPrestador));
    }

    /** E8: el expediente de un prestador para el gestor. 404 si no existe o está dado de baja. */
    @Transactional(readOnly = true)
    public ExpedienteResponse expedienteDe(UUID uuidPrestador) {
        return expediente(buscar(uuidPrestador));
    }

    /** E8b: bytes de un documento vigente de ese prestador; cualquier otro caso, 404. */
    @Transactional(readOnly = true)
    public DocumentoLeido leer(UUID uuidPrestador, UUID uuidDocumento) {
        Prestador prestador = buscar(uuidPrestador);
        DocumentoValidacion documento = documentos.findByUuidAndIdPrestadorAndEliminadoEnIsNull(uuidDocumento, prestador.getId())
            .orElseThrow(ServicioDeDocumentos::noEncontrado);
        try {
            return new DocumentoLeido(documento.getMime(), documento.getBytes(), almacen.leer(documento.getRuta()));
        } catch (ArchivoFaltanteException e) {
            // Resuelto el expediente, los archivos se borran y quedan solo las certificaciones aprobadas como dato.
            log.info("El documento {} ya no tiene archivo", uuidDocumento);
            throw noEncontrado();
        }
    }

    /**
     * Lo que E6 exige para aprobar (D01): DNI frente y dorso y la declaración jurada (409
     * EXPEDIENTE_INCOMPLETO) y, si el rubro requiere matrícula, al menos una certificación (409 FALTA_MATRICULA).
     */
    void exigirParaAprobar(Prestador prestador) {
        List<TipoDeDocumento> tipos = documentos.findByIdPrestadorAndEliminadoEnIsNullOrderByCreadoEnAscIdAsc(prestador.getId())
            .stream().map(DocumentoValidacion::getTipo).toList();
        if (prestador.getDeclaracionJuradaEn() == null || !tipos.contains(TipoDeDocumento.DNI_FRENTE)
                || !tipos.contains(TipoDeDocumento.DNI_DORSO)) {
            throw new ConflictoException("EXPEDIENTE_INCOMPLETO",
                "Falta el DNI (frente y dorso) o la declaración jurada del prestador.");
        }
        if (prestador.getTipoServicio().isRequiereMatricula() && !tipos.contains(TipoDeDocumento.CERTIFICACION)) {
            throw new ConflictoException("FALTA_MATRICULA",
                "El rubro " + prestador.getTipoServicio().getNombre() + " requiere matrícula y no hay ninguna certificación.");
        }
    }

    /**
     * Cierre del expediente al decidir (E6): los documentos toman el estado de la decisión; se dan de baja
     * los DNI y las certificaciones rechazadas, y quedan como dato las certificaciones aprobadas. Los
     * archivos de todos se borran del disco después del commit (Ley 25.326).
     */
    void cerrarExpediente(Long idPrestador, EstadoValidacion decision) {
        List<DocumentoValidacion> vigentes = documentos.findByIdPrestadorAndEliminadoEnIsNullOrderByCreadoEnAscIdAsc(idPrestador);
        for (DocumentoValidacion d : vigentes) {
            d.setEstado(decision);
            if (d.getTipo().esDni() || decision == EstadoValidacion.RECHAZADO) {
                d.darDeBaja();
            }
            String nombre = d.getRuta();
            alTerminar(estado -> {
                if (estado == TransactionSynchronization.STATUS_COMMITTED) {
                    almacen.borrar(nombre);
                }
            });
        }
        documentos.saveAll(vigentes);
    }

    /** Baja de un DNI reemplazado; su archivo se borra si la carga nueva se confirma. */
    private void darDeBaja(DocumentoValidacion anterior) {
        anterior.darDeBaja();
        String nombre = anterior.getRuta();
        alTerminar(estado -> {
            if (estado == TransactionSynchronization.STATUS_COMMITTED) {
                almacen.borrar(nombre);
            }
        });
    }

    private static void alTerminar(IntConsumer accion) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                accion.accept(estado);
            }
        });
    }

    private Prestador buscar(UUID uuidPrestador) {
        return prestadores.findByUuidAndEliminadoEnIsNull(uuidPrestador)
            .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos al prestador."));
    }

    private ExpedienteResponse expediente(Prestador prestador) {
        return new ExpedienteResponse(prestador.getDeclaracionJuradaEn(),
            documentos.findByIdPrestadorAndEliminadoEnIsNullOrderByCreadoEnAscIdAsc(prestador.getId()).stream()
                .map(ServicioDeDocumentos::aRespuesta).toList());
    }

    private static DocumentoResponse aRespuesta(DocumentoValidacion d) {
        return new DocumentoResponse(d.getUuid(), d.getTipo(), d.getEstado(), d.getCreadoEn());
    }

    private static RecursoNoEncontradoException noEncontrado() {
        return new RecursoNoEncontradoException("No existe el documento.");
    }
}
