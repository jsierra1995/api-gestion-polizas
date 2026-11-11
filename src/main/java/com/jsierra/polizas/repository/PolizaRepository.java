package com.jsierra.polizas.repository;

import com.jsierra.polizas.domain.EstadoPoliza;
import com.jsierra.polizas.domain.Poliza;
import com.jsierra.polizas.domain.TipoPoliza;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PolizaRepository extends JpaRepository<Poliza, Long> {

    /** Filtros opcionales y combinables: un parámetro null no filtra. */
    @Query("""
            select p from Poliza p
            where (:tipo is null or p.tipo = :tipo)
              and (:estado is null or p.estado = :estado)
            order by p.id
            """)
    List<Poliza> buscar(@Param("tipo") TipoPoliza tipo, @Param("estado") EstadoPoliza estado);
}
