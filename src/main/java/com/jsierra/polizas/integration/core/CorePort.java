package com.jsierra.polizas.integration.core;

/**
 * Puerto hacia el CORE de seguros. El resto de la aplicación solo conoce esta interfaz;
 * hoy la implementa un adaptador HTTP contra el mock, mañana el servicio agnóstico de edición en WebLogic.
 */
public interface CorePort {

    void notificarActualizacion(Long polizaId, String operacion);
}
