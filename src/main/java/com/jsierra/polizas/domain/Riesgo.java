package com.jsierra.polizas.domain;

import com.jsierra.polizas.exception.ConflictoEstadoException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Un inmueble arrendado y asegurado dentro de una póliza. */
@Entity
@Table(name = "riesgo", indexes = @Index(name = "idx_riesgo_poliza", columnList = "poliza_id"))
public class Riesgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poliza_id", nullable = false)
    private Poliza poliza;

    /** Asegurado: quien arrienda el inmueble. */
    @Column(nullable = false, length = 150)
    private String arrendatario;

    /** Beneficiario en caso de siniestro: el dueño del inmueble. */
    @Column(nullable = false, length = 150)
    private String arrendador;

    @Column(nullable = false, length = 200)
    private String direccionInmueble;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoRiesgo estado;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaCancelacion;

    protected Riesgo() {
        // requerido por JPA
    }

    private Riesgo(String arrendatario, String arrendador, String direccionInmueble) {
        this.arrendatario = arrendatario;
        this.arrendador = arrendador;
        this.direccionInmueble = direccionInmueble;
        this.estado = EstadoRiesgo.ACTIVO;
        this.fechaCreacion = LocalDateTime.now();
    }

    public static Riesgo nuevo(String arrendatario, String arrendador, String direccionInmueble) {
        return new Riesgo(arrendatario, arrendador, direccionInmueble);
    }

    /** Solo Poliza.agregarRiesgo enlaza el riesgo (mismo paquete). */
    void asignarPoliza(Poliza poliza) {
        this.poliza = poliza;
    }

    /** Solo la póliza (raíz del agregado) cancela sus riesgos. */
    void cancelar(LocalDateTime ahora) {
        if (!estaActivo()) {
            throw new ConflictoEstadoException("El riesgo " + id + " ya está cancelado");
        }
        this.estado = EstadoRiesgo.CANCELADO;
        this.fechaCancelacion = ahora;
    }

    public boolean estaActivo() {
        return estado == EstadoRiesgo.ACTIVO;
    }

    public Long getId() {
        return id;
    }

    public Poliza getPoliza() {
        return poliza;
    }

    public String getArrendatario() {
        return arrendatario;
    }

    public String getArrendador() {
        return arrendador;
    }

    public String getDireccionInmueble() {
        return direccionInmueble;
    }

    public EstadoRiesgo getEstado() {
        return estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaCancelacion() {
        return fechaCancelacion;
    }
}
