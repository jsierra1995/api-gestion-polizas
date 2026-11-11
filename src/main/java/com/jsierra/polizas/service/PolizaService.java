package com.jsierra.polizas.service;

import com.jsierra.polizas.domain.EstadoPoliza;
import com.jsierra.polizas.domain.Poliza;
import com.jsierra.polizas.domain.TipoPoliza;
import com.jsierra.polizas.dto.PolizaResponse;
import com.jsierra.polizas.exception.RecursoNoEncontradoException;
import com.jsierra.polizas.repository.PolizaRepository;
import com.jsierra.polizas.service.PolizaModificada.Operacion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Casos de uso de pólizas. Devuelve DTO para que ninguna entidad salga de la transacción
 * (open-in-view está desactivado). Las reglas viven en la entidad; aquí se orquesta.
 */
@Service
@Transactional(readOnly = true)
public class PolizaService {

    private final PolizaRepository polizaRepository;
    private final ApplicationEventPublisher eventos;
    private final BigDecimal ipc;

    public PolizaService(PolizaRepository polizaRepository,
                         ApplicationEventPublisher eventos,
                         @Value("${app.ipc}") BigDecimal ipc) {
        this.polizaRepository = polizaRepository;
        this.eventos = eventos;
        this.ipc = ipc;
    }

    public List<PolizaResponse> listar(TipoPoliza tipo, EstadoPoliza estado) {
        return polizaRepository.buscar(tipo, estado).stream()
                .map(PolizaResponse::desde)
                .toList();
    }

    /** 404 si no existe; 409 si está cancelada (lo valida la entidad antes de calcular nada). */
    @Transactional
    public PolizaResponse renovar(Long id) {
        Poliza poliza = buscar(id);
        poliza.renovar(ipc);
        eventos.publishEvent(PolizaModificada.dePoliza(id, Operacion.RENOVACION));
        return PolizaResponse.desde(poliza);
    }

    private Poliza buscar(Long id) {
        return polizaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la póliza con id " + id));
    }
}
