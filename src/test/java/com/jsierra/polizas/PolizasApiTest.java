package com.jsierra.polizas;

import com.jsierra.polizas.domain.Poliza;
import com.jsierra.polizas.domain.Riesgo;
import com.jsierra.polizas.integration.core.CorePort;
import com.jsierra.polizas.repository.PolizaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de punta a punta: filtro de API key, controllers, services, JPA y H2.
 * El CORE se reemplaza por un mock para comprobar que cada escritura lo notifica después del commit.
 * Cada prueba crea sus propias pólizas, así no dependen del orden ni de los datos semilla.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PolizasApiTest {

    private static final String HEADER = "x-api-key";
    private static final String API_KEY = "123456";
    private static final String RIESGO =
            "{\"arrendatario\":\"Pedro Gómez\",\"arrendador\":\"Rosa Díaz\",\"direccionInmueble\":\"Calle 1 # 2-3\"}";
    private static final String EVENTO_CORE = "{\"evento\":\"ACTUALIZACION\",\"polizaId\":555}";
    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PolizaRepository polizaRepository;

    @MockitoBean
    private CorePort corePort;

    // ---------- Seguridad ----------

    @Test
    void sinHeaderResponde401ConElCuerpoUniforme() throws Exception {
        mockMvc.perform(get("/polizas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Falta el header x-api-key"))
                .andExpect(jsonPath("$.path").value("/polizas"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void conHeaderErradoResponde401() throws Exception {
        mockMvc.perform(get("/polizas").header(HEADER, "999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("El header x-api-key no es válido"));
    }

    @Test
    void conHeaderCorrectoResponde200() throws Exception {
        mockMvc.perform(get("/polizas").header(HEADER, API_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void elCoreMockTambienExigeElHeader() throws Exception {
        mockMvc.perform(post("/core-mock/evento").contentType(MediaType.APPLICATION_JSON).content(EVENTO_CORE))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/core-mock/evento").header(HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(EVENTO_CORE))
                .andExpect(status().isAccepted());
    }

    // ---------- Consultas y errores ----------

    @Test
    void polizaInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/polizas/999999/riesgos").header(HEADER, API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/polizas/999999/riesgos"));
    }

    @Test
    void unFiltroConValorInvalidoResponde400() throws Exception {
        mockMvc.perform(get("/polizas").param("tipo", "XYZ").header(HEADER, API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("COLECTIVA")));
    }

    @Test
    void losFiltrosDeTipoYEstadoSeCombinan() throws Exception {
        mockMvc.perform(get("/polizas").param("tipo", "COLECTIVA").param("estado", "CANCELADA").header(HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].tipo", everyItem(is("COLECTIVA"))))
                .andExpect(jsonPath("$[*].estado", everyItem(is("CANCELADA"))));
    }

    // ---------- Escrituras: reglas y notificación al CORE ----------

    @Test
    void renovarActualizaLaPolizaYNotificaAlCore() throws Exception {
        Poliza poliza = guardar(colectiva());

        mockMvc.perform(post("/polizas/{id}/renovar", poliza.getId()).header(HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RENOVADA"))
                .andExpect(jsonPath("$.fechaInicioVigencia").value("2027-01-01"))
                .andExpect(jsonPath("$.fechaFinVigencia").value("2027-12-31"));

        verify(corePort).notificarActualizacion(poliza.getId(), "RENOVACION");
    }

    @Test
    void renovarUnaPolizaCanceladaResponde409YNoNotifica() throws Exception {
        Poliza poliza = colectiva();
        poliza.cancelar(LocalDateTime.now());
        guardar(poliza);

        mockMvc.perform(post("/polizas/{id}/renovar", poliza.getId()).header(HEADER, API_KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        verify(corePort, never()).notificarActualizacion(anyLong(), anyString());
    }

    @Test
    void cancelarLaPolizaCancelaSusRiesgosYNotificaAlCore() throws Exception {
        Poliza poliza = guardar(colectiva());

        mockMvc.perform(post("/polizas/{id}/cancelar", poliza.getId()).header(HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
        mockMvc.perform(get("/polizas/{id}/riesgos", poliza.getId()).header(HEADER, API_KEY))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].estado", everyItem(is("CANCELADO"))));

        verify(corePort).notificarActualizacion(poliza.getId(), "CANCELACION");
    }

    @Test
    void agregarUnRiesgoAUnaColectivaResponde201YNotificaAlCore() throws Exception {
        Poliza poliza = guardar(colectiva());

        mockMvc.perform(post("/polizas/{id}/riesgos", poliza.getId()).header(HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(RIESGO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.polizaId").value(poliza.getId().intValue()))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        verify(corePort).notificarActualizacion(poliza.getId(), "RIESGO_AGREGADO");
    }

    @Test
    void agregarUnRiesgoAUnaIndividualResponde422YNoNotifica() throws Exception {
        Poliza poliza = guardar(individual());

        mockMvc.perform(post("/polizas/{id}/riesgos", poliza.getId()).header(HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(RIESGO))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value(422));

        verify(corePort, never()).notificarActualizacion(anyLong(), anyString());
    }

    @Test
    void unRiesgoConCamposVaciosResponde400() throws Exception {
        Poliza poliza = guardar(colectiva());

        mockMvc.perform(post("/polizas/{id}/riesgos", poliza.getId()).header(HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"arrendatario\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("direccionInmueble")));
    }

    @Test
    void cancelarUnRiesgoDeColectivaNotificaAlCoreYNoSeRepite() throws Exception {
        Poliza poliza = guardar(colectiva());
        Long riesgoId = poliza.getRiesgos().get(0).getId();

        mockMvc.perform(post("/riesgos/{id}/cancelar", riesgoId).header(HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));
        mockMvc.perform(post("/riesgos/{id}/cancelar", riesgoId).header(HEADER, API_KEY))
                .andExpect(status().isConflict());

        verify(corePort).notificarActualizacion(poliza.getId(), "RIESGO_CANCELADO");
    }

    @Test
    void elRiesgoDeUnaIndividualNoSeCancelaPorSeparado() throws Exception {
        Poliza poliza = guardar(individual());
        Long riesgoId = poliza.getRiesgos().get(0).getId();

        mockMvc.perform(post("/riesgos/{id}/cancelar", riesgoId).header(HEADER, API_KEY))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.message", containsString("POST /polizas/" + poliza.getId() + "/cancelar")));
        mockMvc.perform(get("/polizas/{id}/riesgos", poliza.getId()).header(HEADER, API_KEY))
                .andExpect(jsonPath("$[0].estado").value("ACTIVO"));

        verify(corePort, never()).notificarActualizacion(anyLong(), anyString());
    }

    @Test
    void siElCoreFallaLaOperacionDelUsuarioNoSeRevierte() throws Exception {
        doThrow(new IllegalStateException("CORE caído")).when(corePort).notificarActualizacion(anyLong(), anyString());
        Poliza poliza = guardar(colectiva());

        mockMvc.perform(post("/polizas/{id}/renovar", poliza.getId()).header(HEADER, API_KEY))
                .andExpect(status().isOk());
        mockMvc.perform(get("/polizas").param("estado", "RENOVADA").header(HEADER, API_KEY))
                .andExpect(jsonPath("$[*].numero", hasItem(poliza.getNumero())));
    }

    // ---------- Datos propios de cada prueba ----------

    private Poliza guardar(Poliza poliza) {
        return polizaRepository.save(poliza);
    }

    private static Poliza colectiva() {
        Poliza poliza = Poliza.colectiva(numero("COL"), "Inmobiliaria de Prueba", LocalDate.of(2026, 1, 1), 12,
                new BigDecimal("1800000.00"));
        poliza.agregarRiesgo(Riesgo.nuevo("Arrendatario A", "Arrendador A", "Dirección A"));
        poliza.agregarRiesgo(Riesgo.nuevo("Arrendatario B", "Arrendador B", "Dirección B"));
        return poliza;
    }

    private static Poliza individual() {
        return Poliza.individual(numero("IND"), "Arrendatario", "Arrendador", "Dirección",
                LocalDate.of(2026, 9, 1), 6, new BigDecimal("2000000.00"));
    }

    private static String numero(String prefijo) {
        return prefijo + "-T" + SECUENCIA.incrementAndGet();
    }
}
