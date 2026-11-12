package com.jsierra.polizas.integration.core;

import com.jsierra.polizas.service.PolizaModificada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escucha los cambios de estado y avisa al CORE solo DESPUÉS del commit:
 * nunca se notifica al CORE algo que luego se revirtió en nuestra base.
 */
@Component
public class NotificadorCore {

    private static final Logger log = LoggerFactory.getLogger(NotificadorCore.class);

    private final CorePort corePort;

    public NotificadorCore(CorePort corePort) {
        this.corePort = corePort;
    }

    /**
     * El cambio ya está confirmado cuando esto corre. Si el puerto lanza una excepción y se deja
     * propagar, el usuario recibiría un 500 por una operación que sí quedó guardada; por eso se captura
     * aquí, sin depender de que cada adaptador lo haga.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alModificarPoliza(PolizaModificada evento) {
        try {
            corePort.notificarActualizacion(evento.polizaId(), evento.operacion().name());
        } catch (RuntimeException ex) {
            log.error("Fallo al notificar al CORE: polizaId={} operacion={}. Pendiente de reintento (outbox en producción)",
                    evento.polizaId(), evento.operacion(), ex);
        }
    }
}
