package com.jsierra.polizas.service;

/**
 * Evento interno: una póliza o uno de sus riesgos cambió de estado.
 * Los services lo publican dentro de la transacción; quien lo escucha (la notificación al CORE)
 * actúa solo después del commit. En el diseño objetivo, este mismo evento alimenta las notificaciones.
 */
public record PolizaModificada(Long polizaId, Long riesgoId, Operacion operacion) {

    public enum Operacion {
        RENOVACION,
        CANCELACION,
        RIESGO_AGREGADO,
        RIESGO_CANCELADO
    }

    public static PolizaModificada dePoliza(Long polizaId, Operacion operacion) {
        return new PolizaModificada(polizaId, null, operacion);
    }

    public static PolizaModificada deRiesgo(Long polizaId, Long riesgoId, Operacion operacion) {
        return new PolizaModificada(polizaId, riesgoId, operacion);
    }
}
