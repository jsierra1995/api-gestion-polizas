package com.jsierra.polizas.integration.core;

/** Payload que espera el servicio de edición: {"evento":"ACTUALIZACION","polizaId":555}. */
record EventoCore(String evento, Long polizaId) {
}
