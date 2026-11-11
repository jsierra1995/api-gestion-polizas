package com.jsierra.polizas.service;

import com.jsierra.polizas.domain.EstadoPoliza;
import com.jsierra.polizas.domain.TipoPoliza;
import com.jsierra.polizas.dto.PolizaResponse;
import com.jsierra.polizas.repository.PolizaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso de pólizas. Devuelve DTO para que ninguna entidad salga de la transacción
 * (open-in-view está desactivado).
 */
@Service
@Transactional(readOnly = true)
public class PolizaService {

    private final PolizaRepository polizaRepository;

    public PolizaService(PolizaRepository polizaRepository) {
        this.polizaRepository = polizaRepository;
    }

    public List<PolizaResponse> listar(TipoPoliza tipo, EstadoPoliza estado) {
        return polizaRepository.buscar(tipo, estado).stream()
                .map(PolizaResponse::desde)
                .toList();
    }
}
