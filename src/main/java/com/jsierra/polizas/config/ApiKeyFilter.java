package com.jsierra.polizas.config;

import com.jsierra.polizas.exception.ApiKeyInvalidaException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Exige el header x-api-key en /polizas, /riesgos y /core-mock. La consola de H2 queda fuera.
 * El 401 se delega al manejador global para que tenga el mismo cuerpo que el resto de errores.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "x-api-key";
    private static final List<String> RUTAS_PROTEGIDAS = List.of("/polizas", "/riesgos", "/core-mock");

    private final byte[] apiKeyEsperada;
    private final HandlerExceptionResolver resolver;

    public ApiKeyFilter(@Value("${app.security.api-key}") String apiKey,
                        @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.apiKeyEsperada = apiKey.getBytes(StandardCharsets.UTF_8);
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        return RUTAS_PROTEGIDAS.stream().noneMatch(p -> ruta.equals(p) || ruta.startsWith(p + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibida = request.getHeader(HEADER);
        if (recibida == null || recibida.isBlank()) {
            resolver.resolveException(request, response, null, new ApiKeyInvalidaException("Falta el header x-api-key"));
            return;
        }
        // Comparación en tiempo constante: no revela por tiempo de respuesta cuántos caracteres coinciden.
        if (!MessageDigest.isEqual(apiKeyEsperada, recibida.getBytes(StandardCharsets.UTF_8))) {
            resolver.resolveException(request, response, null, new ApiKeyInvalidaException("El header x-api-key no es válido"));
            return;
        }
        chain.doFilter(request, response);
    }
}
