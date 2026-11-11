package com.jsierra.polizas.integration.core;

import com.jsierra.polizas.service.PolizaModificada;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escucha los cambios de estado y avisa al CORE solo DESPUÉS del commit:
 * nunca se notifica al CORE algo que luego se revirtió en nuestra base.
 */
@Component
public class NotificadorCore {

    private final CorePort corePort;

    public NotificadorCore(CorePort corePort) {
        this.corePort = corePort;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alModificarPoliza(PolizaModificada evento) {
        corePort.notificarActualizacion(evento.polizaId(), evento.operacion().name());
    }
}
