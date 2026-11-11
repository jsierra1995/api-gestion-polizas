package com.jsierra.polizas.exception;

/** Falta el header x-api-key o no coincide. El manejador global la traduce a HTTP 401. */
public class ApiKeyInvalidaException extends RuntimeException {

    public ApiKeyInvalidaException(String message) {
        super(message);
    }
}
