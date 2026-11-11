package com.jsierra.polizas.repository;

import com.jsierra.polizas.domain.Riesgo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiesgoRepository extends JpaRepository<Riesgo, Long> {

    List<Riesgo> findByPolizaIdOrderByIdAsc(Long polizaId);
}
