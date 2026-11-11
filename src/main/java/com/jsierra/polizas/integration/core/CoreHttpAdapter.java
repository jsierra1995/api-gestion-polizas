package com.jsierra.polizas.integration.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Adaptador HTTP del puerto CORE. Timeouts cortos para no colgar al usuario si el CORE no responde.
 * Si falla, registra el error y no relanza: la operación del usuario ya quedó confirmada.
 * En producción el evento iría a una tabla outbox y un proceso lo reintentaría con backoff.
 */
@Component
public class CoreHttpAdapter implements CorePort {

    private static final Logger log = LoggerFactory.getLogger(CoreHttpAdapter.class);

    private final RestClient restClient;
    private final String apiKey;

    public CoreHttpAdapter(@Value("${app.core.base-url}") String baseUrl,
                           @Value("${app.security.api-key}") String apiKey) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeouts)
                .build();
        this.apiKey = apiKey;
    }

    @Override
    public void notificarActualizacion(Long polizaId, String operacion) {
        try {
            restClient.post()
                    .uri("/core-mock/evento")
                    .header("x-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EventoCore("ACTUALIZACION", polizaId))
                    .retrieve()
                    .toBodilessEntity();
            log.info("CORE notificado: polizaId={} operacion={}", polizaId, operacion);
        } catch (RuntimeException ex) {
            log.error("No se pudo notificar al CORE: polizaId={} operacion={}. Pendiente de reintento (outbox en producción)",
                    polizaId, operacion, ex);
        }
    }
}
