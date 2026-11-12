package com.jsierra.polizas.integration.core;

import com.jsierra.polizas.service.PolizaModificada;
import com.jsierra.polizas.service.PolizaModificada.Operacion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** La notificación al CORE nunca debe convertir un cambio ya guardado en un error para el usuario. */
class NotificadorCoreTest {

    @Test
    void enviaAlCoreLaPolizaYLaOperacion() {
        List<String> enviados = new ArrayList<>();
        NotificadorCore notificador = new NotificadorCore((polizaId, operacion) -> enviados.add(polizaId + ":" + operacion));

        notificador.alModificarPoliza(PolizaModificada.dePoliza(7L, Operacion.RENOVACION));

        assertEquals(List.of("7:RENOVACION"), enviados);
    }

    @Test
    void siElPuertoFallaNoPropagaLaExcepcion() {
        CorePort puertoCaido = (polizaId, operacion) -> {
            throw new IllegalStateException("CORE caído");
        };
        NotificadorCore notificador = new NotificadorCore(puertoCaido);

        assertDoesNotThrow(() -> notificador.alModificarPoliza(PolizaModificada.dePoliza(1L, Operacion.CANCELACION)));
    }

    @Test
    void elAdaptadorHttpTampocoPropagaSiElCoreNoResponde() {
        CoreHttpAdapter adaptador = new CoreHttpAdapter("http://localhost:1", "123456"); // puerto sin servicio

        assertDoesNotThrow(() -> adaptador.notificarActualizacion(1L, "RENOVACION"));
    }
}
