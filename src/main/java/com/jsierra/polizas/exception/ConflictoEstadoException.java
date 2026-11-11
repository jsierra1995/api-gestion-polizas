package com.jsierra.polizas.exception;

/**
 * La operación no es válida para el estado actual del recurso (por ejemplo, renovar una póliza cancelada).
 * El manejador global la traduce a HTTP 409.
 */
public class ConflictoEstadoException extends RuntimeException {

    public ConflictoEstadoException(String message) {
        super(message);
    }
}
