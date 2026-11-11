package com.jsierra.polizas.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Traduce excepciones a HTTP con un cuerpo uniforme:
 * 400 entrada inválida, 401 API key, 404 no existe, 409 conflicto de estado, 422 regla de negocio,
 * 500 inesperado.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 422 "Unprocessable Content" (RFC 9110); se toma por código para no depender del nombre de la constante. */
    private static final HttpStatus UNPROCESSABLE = HttpStatus.valueOf(422);

    /** Lanzada por el filtro de API key y resuelta aquí para mantener el mismo formato de error. */
    @ExceptionHandler(ApiKeyInvalidaException.class)
    public ResponseEntity<RespuestaError> apiKeyInvalida(ApiKeyInvalidaException ex, HttpServletRequest request) {
        return responder(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<RespuestaError> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest request) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictoEstadoException.class)
    public ResponseEntity<RespuestaError> conflictoEstado(ConflictoEstadoException ex, HttpServletRequest request) {
        return responder(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<RespuestaError> reglaNegocio(ReglaNegocioException ex, HttpServletRequest request) {
        return responder(UNPROCESSABLE, ex.getMessage(), request);
    }

    /** Ej.: GET /polizas?tipo=XYZ. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RespuestaError> parametroInvalido(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        String mensaje = "El parámetro '" + ex.getName() + "' tiene un valor inválido: " + ex.getValue();
        Class<?> tipo = ex.getRequiredType();
        if (tipo != null && tipo.isEnum()) {
            mensaje += ". Valores permitidos: " + Arrays.toString(tipo.getEnumConstants());
        }
        return responder(HttpStatus.BAD_REQUEST, mensaje, request);
    }

    /** Body que no cumple las anotaciones de Bean Validation (@NotBlank, etc.). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaError> validacion(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return responder(HttpStatus.BAD_REQUEST, mensaje, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespuestaError> cuerpoIlegible(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la petición falta o no es un JSON válido", request);
    }

    /** Dos operaciones modificaron la misma póliza a la vez (@Version). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<RespuestaError> concurrencia(ObjectOptimisticLockingFailureException ex,
                                                       HttpServletRequest request) {
        return responder(HttpStatus.CONFLICT,
                "La póliza fue modificada por otra operación al mismo tiempo; consulte e intente de nuevo", request);
    }

    /**
     * Las excepciones propias de Spring MVC (ruta inexistente 404, método no permitido 405, etc.)
     * conservan su código; solo lo realmente inesperado sale como 500, sin detalles internos.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaError> inesperado(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorSpring) {
            return responder(errorSpring.getStatusCode(), errorSpring.getBody().getDetail(), request);
        }
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno. Consulte los logs del servicio", request);
    }

    private ResponseEntity<RespuestaError> responder(HttpStatusCode status, String mensaje,
                                                     HttpServletRequest request) {
        HttpStatus conocido = HttpStatus.resolve(status.value());
        String error = conocido != null ? conocido.getReasonPhrase() : String.valueOf(status.value());
        RespuestaError cuerpo = RespuestaError.de(status.value(), error,
                mensaje != null ? mensaje : error, request.getRequestURI());
        return ResponseEntity.status(status).body(cuerpo);
    }
}
