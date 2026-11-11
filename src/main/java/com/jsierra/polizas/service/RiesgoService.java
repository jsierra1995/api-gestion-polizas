package com.jsierra.polizas.service;

import com.jsierra.polizas.dto.RiesgoResponse;
import com.jsierra.polizas.exception.RecursoNoEncontradoException;
import com.jsierra.polizas.repository.PolizaRepository;
import com.jsierra.polizas.repository.RiesgoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Casos de uso de riesgos (el "Servicio de Riesgos" del diseño del módulo 1). */
@Service
@Transactional(readOnly = true)
public class RiesgoService {

    private final PolizaRepository polizaRepository;
    private final RiesgoRepository riesgoRepository;

    public RiesgoService(PolizaRepository polizaRepository, RiesgoRepository riesgoRepository) {
        this.polizaRepository = polizaRepository;
        this.riesgoRepository = riesgoRepository;
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
}
