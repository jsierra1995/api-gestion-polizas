package com.jsierra.polizas.service;

import com.jsierra.polizas.domain.Poliza;
import com.jsierra.polizas.domain.Riesgo;
import com.jsierra.polizas.domain.TipoPoliza;
import com.jsierra.polizas.dto.RiesgoRequest;
import com.jsierra.polizas.dto.RiesgoResponse;
import com.jsierra.polizas.exception.RecursoNoEncontradoException;
import com.jsierra.polizas.exception.ReglaNegocioException;
import com.jsierra.polizas.repository.PolizaRepository;
import com.jsierra.polizas.repository.RiesgoRepository;
import com.jsierra.polizas.service.PolizaModificada.Operacion;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Casos de uso de riesgos (el "Servicio de Riesgos" del diseño del módulo 1). */
@Service
@Transactional(readOnly = true)
public class RiesgoService {

    private final PolizaRepository polizaRepository;
    private final RiesgoRepository riesgoRepository;
    private final ApplicationEventPublisher eventos;

    public RiesgoService(PolizaRepository polizaRepository,
                         RiesgoRepository riesgoRepository,
                         ApplicationEventPublisher eventos) {
        this.polizaRepository = polizaRepository;
        this.riesgoRepository = riesgoRepository;
        this.eventos = eventos;
    }

    /** Incluye los riesgos cancelados: el estado viene en cada uno. */
    public List<RiesgoResponse> listarPorPoliza(Long polizaId) {
        if (!polizaRepository.existsById(polizaId)) {
            throw new RecursoNoEncontradoException("No existe la póliza con id " + polizaId);
        }
        return riesgoRepository.findByPolizaIdOrderByIdAsc(polizaId).stream()
                .map(RiesgoResponse::desde)
                .toList();
    }

    /**
     * Regla del caso de uso: este endpoint solo aplica a colectivas (422 si es individual).
     * La entidad valida además sus invariantes: 409 si la póliza está cancelada.
     */
    @Transactional
    public RiesgoResponse agregar(Long polizaId, RiesgoRequest request) {
        Poliza poliza = buscarParaModificar(polizaId);
        if (poliza.getTipo() != TipoPoliza.COLECTIVA) {
            throw new ReglaNegocioException("Solo las pólizas COLECTIVAS admiten agregar riesgos; la póliza "
                    + polizaId + " es INDIVIDUAL");
        }
        Riesgo riesgo = Riesgo.nuevo(request.arrendatario(), request.arrendador(), request.direccionInmueble());
        poliza.agregarRiesgo(riesgo);
        Riesgo guardado = riesgoRepository.save(riesgo); // persiste ya para devolver el id generado
        eventos.publishEvent(PolizaModificada.deRiesgo(polizaId, guardado.getId(), Operacion.RIESGO_AGREGADO));
        return RiesgoResponse.desde(guardado);
    }

    /**
     * 404 si el riesgo no existe. 422 sin ningún cambio si pertenece a una individual: se indica
     * cancelar la póliza completa. 409 si el riesgo ya estaba cancelado.
     */
    @Transactional
    public RiesgoResponse cancelar(Long riesgoId) {
        Riesgo riesgo = riesgoRepository.findById(riesgoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el riesgo con id " + riesgoId));
        Long polizaId = riesgo.getPoliza().getId();
        Poliza poliza = buscarParaModificar(polizaId);
        if (poliza.getTipo() == TipoPoliza.INDIVIDUAL) {
            throw new ReglaNegocioException("La póliza " + polizaId + " es INDIVIDUAL: su único riesgo no se cancela"
                    + " por separado. Use POST /polizas/" + polizaId + "/cancelar");
        }
        Riesgo cancelado = poliza.cancelarRiesgo(riesgoId, LocalDateTime.now());
        eventos.publishEvent(PolizaModificada.deRiesgo(polizaId, riesgoId, Operacion.RIESGO_CANCELADO));
        return RiesgoResponse.desde(cancelado);
    }

    private Poliza buscarParaModificar(Long polizaId) {
        return polizaRepository.buscarParaModificar(polizaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la póliza con id " + polizaId));
    }
}
