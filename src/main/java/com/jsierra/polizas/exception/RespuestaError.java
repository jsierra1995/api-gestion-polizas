package com.jsierra.polizas.exception;

import java.time.Instant;

/** Cuerpo uniforme para todos los errores de la API, incluido el 401 del filtro de API key. */
public record RespuestaError(Instant timestamp, int status, String error, String message, String path) {

    public static RespuestaError de(int status, String error, String message, String path) {
        return new RespuestaError(Instant.now(), status, error, message, path);
    }
}
