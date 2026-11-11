package com.jsierra.polizas.controller;

import com.jsierra.polizas.domain.EstadoPoliza;
import com.jsierra.polizas.domain.TipoPoliza;
import com.jsierra.polizas.dto.PolizaResponse;
import com.jsierra.polizas.dto.RiesgoRequest;
import com.jsierra.polizas.dto.RiesgoResponse;
import com.jsierra.polizas.service.PolizaService;
import com.jsierra.polizas.service.RiesgoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Solo traduce HTTP a casos de uso: sin lógica de negocio. */
@RestController
@RequestMapping("/polizas")
public class PolizaController {

    private final PolizaService polizaService;
    private final RiesgoService riesgoService;

    public PolizaController(PolizaService polizaService, RiesgoService riesgoService) {
        this.polizaService = polizaService;
        this.riesgoService = riesgoService;
    }

    /** GET /polizas?tipo=COLECTIVA&estado=ACTIVA (ambos opcionales). */
    @GetMapping
    public List<PolizaResponse> listar(@RequestParam(required = false) TipoPoliza tipo,
                                       @RequestParam(required = false) EstadoPoliza estado) {
        return polizaService.listar(tipo, estado);
    }

    @GetMapping("/{id}/riesgos")
    public List<RiesgoResponse> listarRiesgos(@PathVariable Long id) {
        return riesgoService.listarPorPoliza(id);
    }

    @PostMapping("/{id}/renovar")
    public PolizaResponse renovar(@PathVariable Long id) {
        return polizaService.renovar(id);
    }

    @PostMapping("/{id}/cancelar")
    public PolizaResponse cancelar(@PathVariable Long id) {
        return polizaService.cancelar(id);
    }

    /** 201 con el riesgo creado. Solo para pólizas colectivas. */
    @PostMapping("/{id}/riesgos")
    public ResponseEntity<RiesgoResponse> agregarRiesgo(@PathVariable Long id,
                                                        @Valid @RequestBody RiesgoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(riesgoService.agregar(id, request));
    }
}
