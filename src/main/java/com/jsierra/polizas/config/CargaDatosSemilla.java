package com.jsierra.polizas.config;

import com.jsierra.polizas.domain.Poliza;
import com.jsierra.polizas.domain.Riesgo;
import com.jsierra.polizas.repository.PolizaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos de prueba al arrancar. Se crean con los mismos métodos de dominio que usa la API,
 * así la semilla no puede violar ninguna regla. Sobre una base en memoria recién creada
 * los IDs salen siempre iguales (ver README).
 */
@Component
public class CargaDatosSemilla implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CargaDatosSemilla.class);

    private final PolizaRepository polizaRepository;
    private final BigDecimal ipc;

    public CargaDatosSemilla(PolizaRepository polizaRepository, @Value("${app.ipc}") BigDecimal ipc) {
        this.polizaRepository = polizaRepository;
        this.ipc = ipc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (polizaRepository.count() > 0) {
            return;
        }

        // Póliza 1: COLECTIVA activa (riesgos 1, 2 y 3)
        Poliza colectiva = Poliza.colectiva("COL-0001", "Inmobiliaria Los Andes S.A.S.",
                LocalDate.of(2026, 1, 1), 12, new BigDecimal("1800000.00"));
        colectiva.agregarRiesgo(Riesgo.nuevo("Carlos Pérez", "Marta Gómez", "Calle 100 # 15-20 Apto 301, Bogotá"));
        colectiva.agregarRiesgo(Riesgo.nuevo("Diana Torres", "Jorge Ramírez", "Carrera 15 # 85-40 Apto 502, Bogotá"));
        colectiva.agregarRiesgo(Riesgo.nuevo("Felipe Castro", "Ana Morales", "Calle 72 # 10-07 Apto 204, Bogotá"));

        // Póliza 2: INDIVIDUAL activa (riesgo 4)
        Poliza individual = Poliza.individual("IND-0001", "Laura Rojas", "Andrés Molina",
                "Carrera 7 # 45-10 Apto 1102, Bogotá", LocalDate.of(2026, 9, 1), 6, new BigDecimal("2000000.00"));

        // Póliza 3: INDIVIDUAL ya RENOVADA con el IPC configurado (riesgo 5)
        Poliza renovada = Poliza.individual("IND-0002", "Sofía Herrera", "Ricardo Vargas",
                "Avenida 19 # 120-30 Apto 803, Bogotá", LocalDate.of(2025, 11, 1), 12, new BigDecimal("2000000.00"));
        renovada.renovar(ipc);

        polizaRepository.saveAll(List.of(colectiva, individual, renovada));
        log.info("Datos semilla cargados: {} pólizas", polizaRepository.count());
    }
}
