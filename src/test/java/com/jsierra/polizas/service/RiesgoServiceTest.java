package com.jsierra.polizas.service;

import com.jsierra.polizas.domain.EstadoPoliza;
import com.jsierra.polizas.domain.EstadoRiesgo;
import com.jsierra.polizas.domain.Poliza;
import com.jsierra.polizas.domain.Riesgo;
import com.jsierra.polizas.dto.RiesgoRequest;
import com.jsierra.polizas.dto.RiesgoResponse;
import com.jsierra.polizas.exception.ConflictoEstadoException;
import com.jsierra.polizas.exception.RecursoNoEncontradoException;
import com.jsierra.polizas.exception.ReglaNegocioException;
import com.jsierra.polizas.repository.PolizaRepository;
import com.jsierra.polizas.repository.RiesgoRepository;
import com.jsierra.polizas.service.PolizaModificada.Operacion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Casos de uso de riesgos: reglas por tipo y estado de la póliza, y publicación del cambio. */
@ExtendWith(MockitoExtension.class)
class RiesgoServiceTest {

    private static final RiesgoRequest REQUEST = new RiesgoRequest("Pedro Gómez", "Rosa Díaz", "Calle 1 # 2-3");

    @Mock
    private PolizaRepository polizaRepository;

    @Mock
    private RiesgoRepository riesgoRepository;

    @Mock
    private ApplicationEventPublisher eventos;

    @InjectMocks
    private RiesgoService service;

    @Test
    void agregarUnRiesgoAUnaColectivaLoGuardaYPublicaElCambio() {
        Poliza poliza = colectiva(1L);
        when(polizaRepository.buscarParaModificar(1L)).thenReturn(Optional.of(poliza));
        when(riesgoRepository.save(any(Riesgo.class))).thenAnswer(invocacion -> {
            Riesgo riesgo = invocacion.getArgument(0);
            ReflectionTestUtils.setField(riesgo, "id", 8L);
            return riesgo;
        });

        RiesgoResponse respuesta = service.agregar(1L, REQUEST);

        assertEquals(8L, respuesta.id().longValue());
        assertEquals(1L, respuesta.polizaId().longValue());
        assertEquals(EstadoRiesgo.ACTIVO, respuesta.estado());
        assertEquals(3, poliza.getRiesgos().size());
        verify(eventos).publishEvent(PolizaModificada.deRiesgo(1L, 8L, Operacion.RIESGO_AGREGADO));
    }

    @Test
    void soloLasColectivasAdmitenAgregarRiesgos() {
        when(polizaRepository.buscarParaModificar(2L)).thenReturn(Optional.of(individual(2L)));

        assertThrows(ReglaNegocioException.class, () -> service.agregar(2L, REQUEST));

        verifyNoInteractions(riesgoRepository, eventos);
    }

    @Test
    void noSeAgreganRiesgosAUnaPolizaCancelada() {
        Poliza cancelada = colectiva(4L);
        cancelada.cancelar(LocalDateTime.now());
        when(polizaRepository.buscarParaModificar(4L)).thenReturn(Optional.of(cancelada));

        assertThrows(ConflictoEstadoException.class, () -> service.agregar(4L, REQUEST));

        verifyNoInteractions(riesgoRepository, eventos);
    }

    @Test
    void agregarAUnaPolizaInexistenteFallaConNoEncontrado() {
        when(polizaRepository.buscarParaModificar(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.agregar(99L, REQUEST));

        verifyNoInteractions(riesgoRepository, eventos);
    }

    @Test
    void cancelarUnRiesgoDeColectivaPublicaElCambio() {
        Poliza poliza = colectiva(1L);
        Riesgo riesgo = poliza.getRiesgos().get(1);
        when(riesgoRepository.findById(2L)).thenReturn(Optional.of(riesgo));
        when(polizaRepository.buscarParaModificar(1L)).thenReturn(Optional.of(poliza));

        RiesgoResponse respuesta = service.cancelar(2L);

        assertEquals(EstadoRiesgo.CANCELADO, respuesta.estado());
        assertEquals(EstadoRiesgo.ACTIVO, poliza.getRiesgos().get(0).getEstado());
        verify(eventos).publishEvent(PolizaModificada.deRiesgo(1L, 2L, Operacion.RIESGO_CANCELADO));
    }

    @Test
    void cancelarUnRiesgoInexistenteFallaConNoEncontrado() {
        when(riesgoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.cancelar(99L));

        verifyNoInteractions(eventos);
    }

    @Test
    void cancelarUnRiesgoYaCanceladoFallaYNoPublicaNada() {
        Poliza poliza = colectiva(1L);
        Riesgo riesgo = poliza.getRiesgos().get(1);
        poliza.cancelarRiesgo(2L, LocalDateTime.now());
        when(riesgoRepository.findById(2L)).thenReturn(Optional.of(riesgo));
        when(polizaRepository.buscarParaModificar(1L)).thenReturn(Optional.of(poliza));

        assertThrows(ConflictoEstadoException.class, () -> service.cancelar(2L));

        verifyNoInteractions(eventos);
    }

    @Test
    void elRiesgoDeUnaIndividualNoSeCancelaPorSeparadoYNoCambiaNada() {
        Poliza poliza = individual(2L);
        Riesgo riesgo = poliza.getRiesgos().get(0);
        when(riesgoRepository.findById(4L)).thenReturn(Optional.of(riesgo));
        when(polizaRepository.buscarParaModificar(2L)).thenReturn(Optional.of(poliza));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> service.cancelar(4L));

        assertTrue(error.getMessage().contains("POST /polizas/2/cancelar"));
        assertEquals(EstadoRiesgo.ACTIVO, riesgo.getEstado());
        assertEquals(EstadoPoliza.ACTIVA, poliza.getEstado());
        verifyNoInteractions(eventos);
    }

    @Test
    void listarLosRiesgosDeUnaPolizaInexistenteFallaConNoEncontrado() {
        when(polizaRepository.existsById(99L)).thenReturn(false);

        assertThrows(RecursoNoEncontradoException.class, () -> service.listarPorPoliza(99L));
    }

    private static Poliza colectiva(long id) {
        Poliza poliza = Poliza.colectiva("COL-T" + id, "Inmobiliaria de Prueba", LocalDate.of(2026, 1, 1), 12,
                new BigDecimal("1800000.00"));
        ReflectionTestUtils.setField(poliza, "id", id);
        poliza.agregarRiesgo(conId(Riesgo.nuevo("Arrendatario 1", "Arrendador 1", "Dirección 1"), 1L));
        poliza.agregarRiesgo(conId(Riesgo.nuevo("Arrendatario 2", "Arrendador 2", "Dirección 2"), 2L));
        return poliza;
    }

    private static Poliza individual(long id) {
        Poliza poliza = Poliza.individual("IND-T" + id, "Laura Rojas", "Andrés Molina", "Carrera 7 # 45-10",
                LocalDate.of(2026, 9, 1), 6, new BigDecimal("2000000.00"));
        ReflectionTestUtils.setField(poliza, "id", id);
        conId(poliza.getRiesgos().get(0), 4L);
        return poliza;
    }

    private static Riesgo conId(Riesgo riesgo, long id) {
        ReflectionTestUtils.setField(riesgo, "id", id);
        return riesgo;
    }
}
