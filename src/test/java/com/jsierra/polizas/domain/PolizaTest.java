package com.jsierra.polizas.domain;

import com.jsierra.polizas.exception.ConflictoEstadoException;
import com.jsierra.polizas.exception.RecursoNoEncontradoException;
import com.jsierra.polizas.exception.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas del agregado Poliza, sin Spring ni base de datos. */
class PolizaTest {

    private static final BigDecimal IPC = new BigDecimal("0.05");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 12, 10, 0);

    @Test
    void laPrimaEsElCanonPorLosMesesDeVigencia() {
        Poliza poliza = colectivaConDosRiesgos();

        assertEquals(new BigDecimal("21600000.00"), poliza.getPrima());
        assertEquals(LocalDate.of(2026, 12, 31), poliza.getFechaFinVigencia());
    }

    @Test
    void laIndividualNaceConSuUnicoRiesgoYElArrendatarioEsElTomador() {
        Poliza poliza = individual();
        Riesgo riesgo = poliza.getRiesgos().get(0);

        assertEquals(1, poliza.getRiesgos().size());
        assertEquals("Laura Rojas", poliza.getTomador());
        assertEquals(poliza.getTomador(), riesgo.getArrendatario());
        assertEquals("Andrés Molina", riesgo.getArrendador());
    }

    @Test
    void unaIndividualNoAdmiteUnSegundoRiesgo() {
        Poliza poliza = individual();

        assertThrows(ReglaNegocioException.class,
                () -> poliza.agregarRiesgo(Riesgo.nuevo("Otro", "Otro", "Otra dirección")));
        assertEquals(1, poliza.getRiesgos().size());
    }

    @Test
    void losRiesgosSoloSeAgreganATravesDeLaPoliza() {
        Poliza poliza = colectivaConDosRiesgos();

        assertThrows(UnsupportedOperationException.class,
                () -> poliza.getRiesgos().add(Riesgo.nuevo("X", "Y", "Z")));
    }

    @Test
    void renovarAplicaElIpcAlCanonYRecalculaPrimaYVigencia() {
        Poliza poliza = colectivaConDosRiesgos();

        poliza.renovar(IPC);

        assertEquals(EstadoPoliza.RENOVADA, poliza.getEstado());
        assertEquals(new BigDecimal("1890000.00"), poliza.getCanonMensual());
        assertEquals(new BigDecimal("22680000.00"), poliza.getPrima());
        assertEquals(12, poliza.getMesesVigencia());
        assertEquals(LocalDate.of(2027, 1, 1), poliza.getFechaInicioVigencia());
        assertEquals(LocalDate.of(2027, 12, 31), poliza.getFechaFinVigencia());
    }

    @Test
    void elCanonRenovadoSeRedondeaADosDecimalesConHalfUp() {
        Poliza poliza = Poliza.colectiva("COL-T2", "Inmobiliaria", LocalDate.of(2026, 1, 1), 12,
                new BigDecimal("1000000.10"));

        poliza.renovar(IPC); // 1000000.10 x 1.05 = 1050000.105

        assertEquals(new BigDecimal("1050000.11"), poliza.getCanonMensual()); // HALF_EVEN daría 1050000.10
        assertEquals(new BigDecimal("12600001.32"), poliza.getPrima());
    }

    @Test
    void unaPolizaRenovadaSePuedeVolverARenovar() {
        Poliza poliza = colectivaConDosRiesgos();

        poliza.renovar(IPC);
        poliza.renovar(IPC);

        assertEquals(new BigDecimal("1984500.00"), poliza.getCanonMensual());
        assertEquals(LocalDate.of(2028, 1, 1), poliza.getFechaInicioVigencia());
    }

    @Test
    void noSeRenuevaUnaPolizaCanceladaYNoSeModificaNada() {
        Poliza poliza = colectivaConDosRiesgos();
        poliza.cancelar(AHORA);

        assertThrows(ConflictoEstadoException.class, () -> poliza.renovar(IPC));

        assertEquals(EstadoPoliza.CANCELADA, poliza.getEstado());
        assertEquals(new BigDecimal("1800000.00"), poliza.getCanonMensual());
        assertEquals(LocalDate.of(2026, 1, 1), poliza.getFechaInicioVigencia());
    }

    @Test
    void cancelarLaPolizaCancelaTodosSusRiesgosActivos() {
        Poliza poliza = colectivaConDosRiesgos();
        LocalDateTime ayer = AHORA.minusDays(1);
        poliza.cancelarRiesgo(1L, ayer);

        poliza.cancelar(AHORA);

        assertEquals(EstadoPoliza.CANCELADA, poliza.getEstado());
        assertTrue(poliza.getRiesgos().stream().allMatch(r -> r.getEstado() == EstadoRiesgo.CANCELADO));
        assertEquals(ayer, poliza.getRiesgos().get(0).getFechaCancelacion(), "el riesgo ya cancelado conserva su fecha");
        assertEquals(AHORA, poliza.getRiesgos().get(1).getFechaCancelacion());
    }

    @Test
    void noSeCancelaDosVecesLaMismaPoliza() {
        Poliza poliza = colectivaConDosRiesgos();
        poliza.cancelar(AHORA);

        assertThrows(ConflictoEstadoException.class, () -> poliza.cancelar(AHORA));
    }

    @Test
    void unaPolizaCanceladaNoAdmiteRiesgosNuevos() {
        Poliza poliza = colectivaConDosRiesgos();
        poliza.cancelar(AHORA);

        assertThrows(ConflictoEstadoException.class,
                () -> poliza.agregarRiesgo(Riesgo.nuevo("X", "Y", "Z")));
    }

    @Test
    void cancelarUnRiesgoDeColectivaNoAfectaALosDemasNiALaPoliza() {
        Poliza poliza = colectivaConDosRiesgos();

        Riesgo cancelado = poliza.cancelarRiesgo(2L, AHORA);

        assertEquals(EstadoRiesgo.CANCELADO, cancelado.getEstado());
        assertEquals(AHORA, cancelado.getFechaCancelacion());
        assertEquals(EstadoRiesgo.ACTIVO, poliza.getRiesgos().get(0).getEstado());
        assertEquals(EstadoPoliza.ACTIVA, poliza.getEstado());
    }

    @Test
    void noSeCancelaUnRiesgoYaCancelado() {
        Poliza poliza = colectivaConDosRiesgos();
        poliza.cancelarRiesgo(2L, AHORA);

        assertThrows(ConflictoEstadoException.class, () -> poliza.cancelarRiesgo(2L, AHORA));
    }

    @Test
    void unRiesgoQueNoEsDeLaPolizaNoSeEncuentra() {
        Poliza poliza = colectivaConDosRiesgos();

        assertThrows(RecursoNoEncontradoException.class, () -> poliza.cancelarRiesgo(99L, AHORA));
    }

    @Test
    void elUnicoRiesgoDeUnaIndividualNoSeCancelaPorSeparado() {
        Poliza poliza = individual();

        assertThrows(ReglaNegocioException.class, () -> poliza.cancelarRiesgo(10L, AHORA));

        assertEquals(EstadoRiesgo.ACTIVO, poliza.getRiesgos().get(0).getEstado());
        assertEquals(EstadoPoliza.ACTIVA, poliza.getEstado());
    }

    private static Poliza colectivaConDosRiesgos() {
        Poliza poliza = Poliza.colectiva("COL-T1", "Inmobiliaria de Prueba", LocalDate.of(2026, 1, 1), 12,
                new BigDecimal("1800000.00"));
        poliza.agregarRiesgo(conId(Riesgo.nuevo("Arrendatario 1", "Arrendador 1", "Dirección 1"), 1L));
        poliza.agregarRiesgo(conId(Riesgo.nuevo("Arrendatario 2", "Arrendador 2", "Dirección 2"), 2L));
        return poliza;
    }

    private static Poliza individual() {
        Poliza poliza = Poliza.individual("IND-T1", "Laura Rojas", "Andrés Molina", "Carrera 7 # 45-10",
                LocalDate.of(2026, 9, 1), 6, new BigDecimal("2000000.00"));
        conId(poliza.getRiesgos().get(0), 10L);
        return poliza;
    }

    /** En producción el id lo asigna la base; aquí se fija para identificar cada riesgo. */
    private static Riesgo conId(Riesgo riesgo, long id) {
        ReflectionTestUtils.setField(riesgo, "id", id);
        return riesgo;
    }
}
