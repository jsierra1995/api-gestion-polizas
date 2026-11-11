package com.jsierra.polizas.coremock;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Simula el servicio de edición del CORE. Su único propósito es dejar constancia en el log. */
@RestController
@RequestMapping("/core-mock")
public class CoreMockController {

    private static final Logger log = LoggerFactory.getLogger(CoreMockController.class);

    @PostMapping("/evento")
    public ResponseEntity<Void> recibirEvento(@Valid @RequestBody EventoCoreMockRequest evento) {
        log.info("[CORE-MOCK] Se intentó enviar al CORE: evento={} polizaId={}", evento.evento(), evento.polizaId());
        return ResponseEntity.accepted().build();
    }
}
