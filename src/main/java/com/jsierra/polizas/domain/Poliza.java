package com.jsierra.polizas.domain;

import com.jsierra.polizas.exception.ConflictoEstadoException;
import com.jsierra.polizas.exception.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Raíz del agregado: los riesgos solo se agregan o cambian a través de la póliza,
 * para que sus reglas no se puedan saltar desde otra capa.
 */
@Entity
@Table(name = "poliza", indexes = @Index(name = "idx_poliza_tipo_estado", columnList = "tipo, estado"))
public class Poliza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoPoliza tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoPoliza estado;

    /** Colectiva: la inmobiliaria o administración. Individual: el mismo arrendatario. */
    @Column(nullable = false, length = 150)
    private String tomador;

    @Column(nullable = false)
    private LocalDate fechaInicioVigencia;

    /** Último día cubierto (inclusive). */
    @Column(nullable = false)
    private LocalDate fechaFinVigencia;

    @Column(nullable = false)
    private int mesesVigencia;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal canonMensual;

    /** Siempre canon mensual x meses de vigencia; nunca se recibe desde afuera. */
    @Column(nullable = false, precision = 17, scale = 2)
    private BigDecimal prima;

    @OneToMany(mappedBy = "poliza", cascade = CascadeType.ALL)
    private List<Riesgo> riesgos = new ArrayList<>();

    @Version
    private Long version;

    protected Poliza() {
        // requerido por JPA
    }

    private Poliza(String numero, TipoPoliza tipo, String tomador,
                   LocalDate fechaInicioVigencia, int mesesVigencia, BigDecimal canonMensual) {
        if (mesesVigencia <= 0) {
            throw new IllegalArgumentException("La vigencia debe ser de al menos un mes");
        }
        if (canonMensual == null || canonMensual.signum() <= 0) {
            throw new IllegalArgumentException("El canon mensual debe ser mayor que cero");
        }
        this.numero = numero;
        this.tipo = tipo;
        this.tomador = tomador;
        this.estado = EstadoPoliza.ACTIVA;
        this.fechaInicioVigencia = fechaInicioVigencia;
        this.mesesVigencia = mesesVigencia;
        this.fechaFinVigencia = calcularFinVigencia(fechaInicioVigencia, mesesVigencia);
        this.canonMensual = canonMensual;
        this.prima = calcularPrima(canonMensual, mesesVigencia);
    }

    /** El tomador es la inmobiliaria o administración; los riesgos se agregan después. */
    public static Poliza colectiva(String numero, String tomador, LocalDate fechaInicioVigencia,
                                   int mesesVigencia, BigDecimal canonMensual) {
        return new Poliza(numero, TipoPoliza.COLECTIVA, tomador, fechaInicioVigencia, mesesVigencia, canonMensual);
    }

    /**
     * Tomador y asegurado son la misma persona (el arrendatario), así que el tomador se toma del riesgo
     * y no se recibe por separado: la póliza nace con su único riesgo.
     */
    public static Poliza individual(String numero, String arrendatario, String arrendador, String direccionInmueble,
                                    LocalDate fechaInicioVigencia, int mesesVigencia, BigDecimal canonMensual) {
        Poliza poliza = new Poliza(numero, TipoPoliza.INDIVIDUAL, arrendatario,
                fechaInicioVigencia, mesesVigencia, canonMensual);
        poliza.agregarRiesgo(Riesgo.nuevo(arrendatario, arrendador, direccionInmueble));
        return poliza;
    }

    /** Invariantes del agregado. Que el endpoint solo acepte colectivas lo valida el service. */
    public void agregarRiesgo(Riesgo riesgo) {
        if (estado == EstadoPoliza.CANCELADA) {
            throw new ConflictoEstadoException("La póliza " + numero + " está cancelada y no admite riesgos nuevos");
        }
        if (tipo == TipoPoliza.INDIVIDUAL && !riesgos.isEmpty()) {
            throw new ReglaNegocioException("La póliza individual " + numero + " solo puede tener un riesgo");
        }
        riesgo.asignarPoliza(this);
        riesgos.add(riesgo);
    }

    /**
     * Renueva por el mismo número de meses de la vigencia inicial, empezando el día siguiente al fin
     * de la actual. Canon nuevo = canon x (1 + IPC), redondeado a 2 decimales (HALF_UP);
     * la prima se recalcula con el canon nuevo. Una póliza RENOVADA se puede volver a renovar.
     */
    public void renovar(BigDecimal ipc) {
        if (estado == EstadoPoliza.CANCELADA) {
            throw new ConflictoEstadoException("La póliza " + numero + " está CANCELADA y no se puede renovar");
        }
        if (ipc == null || ipc.signum() < 0) {
            throw new IllegalArgumentException("El IPC configurado debe ser cero o positivo");
        }
        this.canonMensual = canonMensual.multiply(BigDecimal.ONE.add(ipc)).setScale(2, RoundingMode.HALF_UP);
        this.prima = calcularPrima(canonMensual, mesesVigencia);
        this.fechaInicioVigencia = fechaFinVigencia.plusDays(1);
        this.fechaFinVigencia = calcularFinVigencia(fechaInicioVigencia, mesesVigencia);
        this.estado = EstadoPoliza.RENOVADA;
    }

    /** Cancela la póliza y, en el mismo cambio, todos sus riesgos activos. */
    public void cancelar(LocalDateTime ahora) {
        if (estado == EstadoPoliza.CANCELADA) {
            throw new ConflictoEstadoException("La póliza " + numero + " ya está cancelada");
        }
        this.estado = EstadoPoliza.CANCELADA;
        riesgos.stream()
                .filter(Riesgo::estaActivo)
                .forEach(riesgo -> riesgo.cancelar(ahora));
    }

    private static BigDecimal calcularPrima(BigDecimal canonMensual, int mesesVigencia) {
        return canonMensual.multiply(BigDecimal.valueOf(mesesVigencia));
    }

    private static LocalDate calcularFinVigencia(LocalDate inicio, int mesesVigencia) {
        return inicio.plusMonths(mesesVigencia).minusDays(1);
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public TipoPoliza getTipo() {
        return tipo;
    }

    public EstadoPoliza getEstado() {
        return estado;
    }

    public String getTomador() {
        return tomador;
    }

    public LocalDate getFechaInicioVigencia() {
        return fechaInicioVigencia;
    }

    public LocalDate getFechaFinVigencia() {
        return fechaFinVigencia;
    }

    public int getMesesVigencia() {
        return mesesVigencia;
    }

    public BigDecimal getCanonMensual() {
        return canonMensual;
    }

    public BigDecimal getPrima() {
        return prima;
    }

    /** Solo lectura: agregar un riesgo obliga a pasar por agregarRiesgo y sus reglas. */
    public List<Riesgo> getRiesgos() {
        return Collections.unmodifiableList(riesgos);
    }

    public Long getVersion() {
        return version;
    }
}
