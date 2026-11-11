package com.jsierra.polizas.repository;

import com.jsierra.polizas.domain.Poliza;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolizaRepository extends JpaRepository<Poliza, Long> {
}
