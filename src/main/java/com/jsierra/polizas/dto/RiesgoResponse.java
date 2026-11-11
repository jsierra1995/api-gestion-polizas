package com.jsierra.polizas.dto;

import com.jsierra.polizas.domain.EstadoRiesgo;
import com.jsierra.polizas.domain.Riesgo;

import java.time.LocalDateTime;

public record RiesgoResponse(
        Long id,
        Long polizaId,
        String arrendatario,
        String arrendador,
        String direccionInmueble,
        EstadoRiesgo estado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaCancelacion) {

    public static RiesgoResponse desde(Riesgo riesgo) {
        return new RiesgoResponse(
                riesgo.getId(),
                riesgo.getPoliza().getId(),
                riesgo.getArrendatario(),
                riesgo.getArrendador(),
                riesgo.getDireccionInmueble(),
                riesgo.getEstado(),
                riesgo.getFechaCreacion(),
                riesgo.getFechaCancelacion());
    }
}
