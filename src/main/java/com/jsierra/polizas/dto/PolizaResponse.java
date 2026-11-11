package com.jsierra.polizas.dto;

import com.jsierra.polizas.domain.EstadoPoliza;
import com.jsierra.polizas.domain.Poliza;
import com.jsierra.polizas.domain.TipoPoliza;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lo que la API expone de una póliza. Sin riesgos: tienen su propio endpoint y así se evitan consultas N+1. */
public record PolizaResponse(
        Long id,
        String numero,
        TipoPoliza tipo,
        EstadoPoliza estado,
        String tomador,
        LocalDate fechaInicioVigencia,
        LocalDate fechaFinVigencia,
        int mesesVigencia,
        BigDecimal canonMensual,
        BigDecimal prima) {

    public static PolizaResponse desde(Poliza poliza) {
        return new PolizaResponse(
                poliza.getId(),
                poliza.getNumero(),
                poliza.getTipo(),
                poliza.getEstado(),
                poliza.getTomador(),
                poliza.getFechaInicioVigencia(),
                poliza.getFechaFinVigencia(),
                poliza.getMesesVigencia(),
                poliza.getCanonMensual(),
                poliza.getPrima());
    }
}
