package com.jsierra.polizas.service;

import com.jsierra.polizas.domain.EstadoPoliza;
import com.jsierra.polizas.domain.EstadoRiesgo;
import com.jsierra.polizas.domain.Poliza;
import com.jsierra.polizas.domain.Riesgo;
import com.jsierra.polizas.dto.PolizaResponse;
import com.jsierra.polizas.exception.ConflictoEstadoException;
import com.jsierra.polizas.exception.RecursoNoEncontradoException;
import com.jsierra.polizas.repository.PolizaRepository;
import com.jsierra.polizas.service.PolizaModificada.Operacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Casos de uso de pólizas: qué se responde y cuándo se publica el cambio hacia el CORE. */
@ExtendWith(MockitoExtension.class)
class PolizaServiceTest {

    @Mock
    private PolizaRepository polizaRepository;

    @Mock
    private ApplicationEventPublisher eventos;

    private PolizaService service;

    @BeforeEach
    void setUp() {
        service = new PolizaService(polizaRepository, eventos, new BigDecimal("0.05"));
    }

    @Test
    void renovarUsaElIpcConfiguradoYPublicaElCambio() {
        when(polizaRepository.findById(1L)).thenReturn(Optional.of(colectiva(1L)));

        PolizaResponse respuesta = service.renovar(1L);

        assertEquals(EstadoPoliza.RENOVADA, respuesta.estado());
        assertEquals(new BigDecimal("1890000.00"), respuesta.canonMensual());
        assertEquals(new BigDecimal("22680000.00"), respuesta.prima());
        verify(eventos).publishEvent(PolizaModificada.dePoliza(1L, Operacion.RENOVACION));
    }

    @Test
    void renovarUnaCanceladaFallaAntesDeCalcularYNoPublicaNada() {
        Poliza cancelada = colectiva(4L);
        cancelada.cancelar(LocalDateTime.now());
        when(polizaRepository.findById(4L)).thenReturn(Optional.of(cancelada));

        assertThrows(ConflictoEstadoException.class, () -> service.renovar(4L));

        assertEquals(new BigDecimal("1800000.00"), cancelada.getCanonMensual());
        verifyNoInteractions(eventos);
    }

    @Test
    void renovarUnaPolizaInexistenteFallaConNoEncontrado() {
        when(polizaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.renovar(99L));

        verifyNoInteractions(eventos);
    }

    @Test
    void cancelarCancelaLaPolizaYSusRiesgosYPublicaElCambio() {
        Poliza poliza = colectiva(1L);
        when(polizaRepository.findById(1L)).thenReturn(Optional.of(poliza));

        PolizaResponse respuesta = service.cancelar(1L);

        assertEquals(EstadoPoliza.CANCELADA, respuesta.estado());
        assertTrue(poliza.getRiesgos().stream().allMatch(r -> r.getEstado() == EstadoRiesgo.CANCELADO));
        verify(eventos).publishEvent(PolizaModificada.dePoliza(1L, Operacion.CANCELACION));
    }

    @Test
    void cancelarUnaPolizaYaCanceladaFallaYNoPublicaNada() {
        Poliza cancelada = colectiva(4L);
        cancelada.cancelar(LocalDateTime.now());
        when(polizaRepository.findById(4L)).thenReturn(Optional.of(cancelada));

        assertThrows(ConflictoEstadoException.class, () -> service.cancelar(4L));

        verifyNoInteractions(eventos);
    }

    private static Poliza colectiva(long id) {
        Poliza poliza = Poliza.colectiva("COL-T" + id, "Inmobiliaria de Prueba", LocalDate.of(2026, 1, 1), 12,
                new BigDecimal("1800000.00"));
        poliza.agregarRiesgo(Riesgo.nuevo("Arrendatario 1", "Arrendador 1", "Dirección 1"));
        poliza.agregarRiesgo(Riesgo.nuevo("Arrendatario 2", "Arrendador 2", "Dirección 2"));
        ReflectionTestUtils.setField(poliza, "id", id);
        return poliza;
    }
}
