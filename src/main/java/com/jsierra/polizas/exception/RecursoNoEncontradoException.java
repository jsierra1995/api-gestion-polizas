package com.jsierra.polizas.exception;

/** La póliza o el riesgo pedido no existe. El manejador global la traduce a HTTP 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String message) {
        super(message);
    }
}
