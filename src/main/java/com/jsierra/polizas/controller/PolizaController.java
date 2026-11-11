package com.jsierra.polizas.controller;

import com.jsierra.polizas.domain.EstadoPoliza;
import com.jsierra.polizas.domain.TipoPoliza;
import com.jsierra.polizas.dto.PolizaResponse;
import com.jsierra.polizas.dto.RiesgoResponse;
import com.jsierra.polizas.service.PolizaService;
import com.jsierra.polizas.service.RiesgoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
