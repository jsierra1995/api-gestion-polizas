package com.jsierra.polizas.exception;

/**
 * Se incumple una regla de negocio (por ejemplo, un segundo riesgo en una póliza individual).
 * El manejador global la traduce a HTTP 422.
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String message) {
        super(message);
    }
}
