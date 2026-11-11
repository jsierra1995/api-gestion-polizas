package com.jsierra.polizas.repository;

import com.jsierra.polizas.domain.EstadoPoliza;
import com.jsierra.polizas.domain.Poliza;
import com.jsierra.polizas.domain.TipoPoliza;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PolizaRepository extends JpaRepository<Poliza, Long> {

    /** Filtros opcionales y combinables: un parámetro null no filtra. */
    @Query("""
            select p from Poliza p
            where (:tipo is null or p.tipo = :tipo)
              and (:estado is null or p.estado = :estado)
            order by p.id
            """)
    List<Poliza> buscar(@Param("tipo") TipoPoliza tipo, @Param("estado") EstadoPoliza estado);

    /**
     * Para cambios en los riesgos: agregar o cancelar un riesgo no modifica columnas de la póliza,
     * así que su @Version no subiría sola. Se fuerza el incremento para que, por ejemplo, cancelar la
     * póliza y agregarle un riesgo al mismo tiempo no pasen las dos: la segunda recibe 409.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select p from Poliza p where p.id = :id")
    Optional<Poliza> buscarParaModificar(@Param("id") Long id);
}
