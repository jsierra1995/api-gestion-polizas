# API de gestión de pólizas de arrendamiento

API REST para consultar y modificar pólizas de arrendamiento **individuales** y **colectivas** y sus riesgos.
Cada cambio de estado se notifica al CORE de seguros (simulado con un mock) a través de un puerto de integración.

**Stack:** Java 17 · Spring Boot 4.1 · Spring Web MVC · Spring Data JPA · H2 en memoria · Bean Validation ·
JUnit + Mockito + MockMvc.

## Requisitos

- JDK 17 o superior (`java -version`).
- No hace falta instalar Maven: el proyecto trae el wrapper (`mvnw`).
- No hace falta base de datos: H2 se crea en memoria al arrancar y se carga con datos de prueba.

## Cómo correrlo

```bash
git clone https://github.com/jsierra1995/api-gestion-polizas.git
cd api-gestion-polizas
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

La API queda en `http://localhost:8080`. En el log debe aparecer `Datos semilla cargados: 4 pólizas`.

## Cómo probarlo

```bash
./mvnw test                     # Windows: .\mvnw.cmd test
```

| Clase de prueba | Qué cubre |
|---|---|
| `PolizaTest` | Reglas del agregado sin Spring: prima, IPC y redondeo, vigencia, cascada, invariantes de riesgos |
| `PolizaServiceTest`, `RiesgoServiceTest` | Casos de uso con Mockito: códigos de error y que solo los cambios exitosos publican el evento hacia el CORE |
| `PolizasApiTest` | De punta a punta con MockMvc y H2: API key, 404, 400, 409, 422, filtros, cascada y que cada escritura notifica al CORE (puerto simulado) |
| `NotificadorCoreTest` | Un fallo del CORE nunca se propaga al usuario |

## Seguridad

Todas las rutas de `/polizas`, `/riesgos` y `/core-mock` exigen el header:

```
x-api-key: 123456
```

Sin el header, o con un valor distinto, la respuesta es `401` con el formato de error estándar.
El valor se lee de configuración (`app.security.api-key`) y se puede sobrescribir con la variable de entorno `APP_API_KEY`.

## Datos semilla

Se cargan al arrancar con los mismos métodos de dominio que usa la API, así que cumplen todas las reglas.

| Póliza | Número | Tipo | Estado | Tomador | Vigencia | Canon | Prima | Riesgos |
|---|---|---|---|---|---|---|---|---|
| 1 | COL-0001 | COLECTIVA | ACTIVA | Inmobiliaria Los Andes S.A.S. | 2026-01-01 a 2026-12-31 (12 meses) | 1.800.000 | 21.600.000 | 1, 2, 3 |
| 2 | IND-0001 | INDIVIDUAL | ACTIVA | Laura Rojas | 2026-09-01 a 2027-02-28 (6 meses) | 2.000.000 | 12.000.000 | 4 |
| 3 | IND-0002 | INDIVIDUAL | RENOVADA | Sofía Herrera | 2026-11-01 a 2027-10-31 (12 meses) | 2.100.000 | 25.200.000 | 5 |
| 4 | COL-0002 | COLECTIVA | CANCELADA | Administración Edificio Torres del Parque P.H. | 2026-02-01 a 2027-01-31 (12 meses) | 1.500.000 | 18.000.000 | 6, 7 (cancelados) |

Consola de H2: `http://localhost:8080/h2-console` · JDBC URL `jdbc:h2:mem:polizasdb` · usuario `sa` · sin contraseña.

## Endpoints

| Método | Ruta | Éxito | Errores |
|---|---|---|---|
| GET | `/polizas?tipo=&estado=` | 200 (filtros opcionales y combinables) | 400 valor de filtro inválido |
| GET | `/polizas/{id}/riesgos` | 200 (incluye riesgos cancelados) | 404 póliza no existe |
| POST | `/polizas/{id}/renovar` | 200 | 404 · 409 póliza cancelada |
| POST | `/polizas/{id}/cancelar` | 200 | 404 · 409 ya cancelada |
| POST | `/polizas/{id}/riesgos` | 201 | 400 body inválido · 404 · 409 póliza cancelada · 422 póliza individual |
| POST | `/riesgos/{id}/cancelar` | 200 | 404 · 409 ya cancelado · 422 riesgo de póliza individual |
| POST | `/core-mock/evento` | 202 (solo registra en el log) | 400 body inválido |

Valores de `tipo`: `INDIVIDUAL`, `COLECTIVA`. Valores de `estado`: `ACTIVA`, `RENOVADA`, `CANCELADA`.

### Ejemplos con curl

Sintaxis de Bash (Linux, macOS o Git Bash en Windows). Partiendo de los datos semilla:

```bash
H="x-api-key: 123456"

# Consultas
curl -H "$H" "http://localhost:8080/polizas"
curl -H "$H" "http://localhost:8080/polizas?tipo=COLECTIVA&estado=ACTIVA"
curl -H "$H" "http://localhost:8080/polizas/1/riesgos"

# Agregar un riesgo a la colectiva 1 -> 201
curl -X POST -H "$H" -H "Content-Type: application/json" \
  -d '{"arrendatario":"Pedro Gómez","arrendador":"Rosa Díaz","direccionInmueble":"Calle 1 # 2-3, Bogotá"}' \
  "http://localhost:8080/polizas/1/riesgos"

# Cancelar un riesgo de la colectiva 1 -> 200
curl -X POST -H "$H" "http://localhost:8080/riesgos/2/cancelar"

# Renovar la póliza 1 -> 200: canon 1.890.000, prima 22.680.000, vigencia 2027-01-01 a 2027-12-31
curl -X POST -H "$H" "http://localhost:8080/polizas/1/renovar"

# Cancelar la póliza 1 -> 200, y todos sus riesgos quedan CANCELADO
curl -X POST -H "$H" "http://localhost:8080/polizas/1/cancelar"

# Mock del CORE -> 202, deja constancia en el log
curl -X POST -H "$H" -H "Content-Type: application/json" \
  -d '{"evento":"ACTUALIZACION","polizaId":555}' "http://localhost:8080/core-mock/evento"
```

Casos que deben fallar:

```bash
curl -i "http://localhost:8080/polizas"                                    # 401: falta el header
curl -i -H "x-api-key: 999" "http://localhost:8080/polizas"                # 401: header inválido
curl -i -H "$H" "http://localhost:8080/polizas?tipo=XYZ"                   # 400: valor de filtro inválido
curl -i -H "$H" "http://localhost:8080/polizas/99/riesgos"                 # 404: póliza no existe
curl -i -X POST -H "$H" "http://localhost:8080/polizas/4/renovar"          # 409: póliza cancelada
curl -i -X POST -H "$H" "http://localhost:8080/polizas/4/cancelar"         # 409: ya cancelada
curl -i -X POST -H "$H" "http://localhost:8080/riesgos/6/cancelar"         # 409: riesgo ya cancelado
curl -i -X POST -H "$H" "http://localhost:8080/riesgos/4/cancelar"         # 422: riesgo de póliza individual
curl -i -X POST -H "$H" -H "Content-Type: application/json" \
  -d '{"arrendatario":"Ana","arrendador":"Luis","direccionInmueble":"Calle 9"}' \
  "http://localhost:8080/polizas/2/riesgos"                                # 422: póliza individual
curl -i -X POST -H "$H" -H "Content-Type: application/json" \
  -d '{"arrendatario":""}' "http://localhost:8080/polizas/1/riesgos"       # 400: campos obligatorios
```

**En PowerShell 5.1:** use `curl.exe` (no `curl`, que es un alias de `Invoke-WebRequest`). Para enviar un body,
guárdelo en un archivo y use `--data-binary "@riesgo.json"`, porque PowerShell parte los JSON que tienen espacios.

### Formato de error

Todos los errores, incluido el 401 del filtro, tienen el mismo cuerpo:

```json
{
  "timestamp": "2026-11-11T22:39:20.266Z",
  "status": 422,
  "error": "Unprocessable Content",
  "message": "La póliza 2 es INDIVIDUAL: su único riesgo no se cancela por separado. Use POST /polizas/2/cancelar",
  "path": "/riesgos/4/cancelar"
}
```

| Código | Cuándo |
|---|---|
| 400 | Parámetro o body inválido, JSON mal formado |
| 401 | Falta `x-api-key` o no coincide |
| 404 | La póliza o el riesgo no existen |
| 409 | La operación no aplica al estado actual (cancelada, ya cancelado) o hubo una modificación concurrente |
| 422 | Se incumple una regla de negocio (riesgos en individual) |
| 500 | Error inesperado, sin detalles internos en la respuesta |

## Reglas de negocio y dónde se validan

| Regla | Dónde | Respuesta si se incumple |
|---|---|---|
| Una póliza cancelada no se renueva (se valida antes de calcular) | `Poliza.renovar` | 409 |
| Renovación: canon x (1 + IPC), prima = canon x meses, misma duración de vigencia, estado RENOVADA | `Poliza.renovar` | — |
| Cancelar una póliza cancela todos sus riesgos activos, en una sola transacción | `Poliza.cancelar` + `@Transactional` en `PolizaService.cancelar` | 409 si ya estaba cancelada |
| Solo las colectivas admiten agregar riesgos por el endpoint | `RiesgoService.agregar` | 422 |
| Una individual tiene exactamente un riesgo | `Poliza.individual` y `Poliza.agregarRiesgo` | 422 |
| Una póliza cancelada no admite riesgos | `Poliza.agregarRiesgo` | 409 |
| El riesgo de una individual no se cancela por separado | `RiesgoService.cancelar` y `Poliza.cancelarRiesgo` | 422, sin cambios |
| Un riesgo cancelado no se vuelve a cancelar | `Riesgo.cancelar` | 409 |
| Todo cambio de estado se notifica al CORE | `PolizaModificada` → `NotificadorCore` → `CorePort` | — |
| Dos cambios simultáneos sobre la misma póliza | `@Version` + `OPTIMISTIC_FORCE_INCREMENT` en `PolizaRepository.buscarParaModificar` | 409 |

## Integración con el CORE

```
PolizaService / RiesgoService
   └─ publica PolizaModificada (dentro de la transacción)
        └─ NotificadorCore (@TransactionalEventListener AFTER_COMMIT)
             └─ CorePort (interfaz)
                  └─ CoreHttpAdapter ──HTTP + x-api-key──> POST /core-mock/evento
```

- Se notifica **solo después del commit**: el CORE nunca recibe un cambio que luego se revirtió.
- Las operaciones rechazadas (4xx) no notifican nada.
- El adaptador usa timeouts cortos (2 s de conexión, 3 s de lectura). Si el CORE falla, se registra el error y
  **la operación del usuario no se revierte**.
- El servicio no conoce HTTP ni el CORE: depende del puerto. Para conectar el servicio agnóstico de edición en
  WebLogic basta con otro adaptador.

## Supuestos

1. **IPC:** 5 % (`app.ipc: 0.05`), valor de ejemplo configurable. El enunciado no da el valor.
2. **Vigencia:** la fecha fin es inclusiva (2026-01-01 a 2026-12-31). La renovación empieza el día siguiente y dura
   los mismos meses que la vigencia inicial.
3. **Redondeo:** el canon renovado se redondea a 2 decimales con `HALF_UP`; la prima se calcula con el canon ya
   redondeado.
4. **Una póliza RENOVADA se puede volver a renovar** (siguiente periodo). Se sobrescriben canon, prima y vigencia;
   no se guarda historial (ver mejoras).
5. **Canon en colectivas:** como indica el enunciado, el canon es de la póliza; agregar o cancelar riesgos no cambia
   la prima.
6. **Cancelar el último riesgo activo de una colectiva** deja la póliza ACTIVA, a la espera de riesgos nuevos.
7. **Cancelar el riesgo de una individual** responde 422 sin cambios e indica usar `POST /polizas/{id}/cancelar`,
   porque dejaría una póliza activa sin riesgo.
8. **Los riesgos cancelados se siguen listando**, con su estado y fecha de cancelación.
9. **No hay creación de pólizas** porque el enunciado no la pide; los datos semilla cubren los cuatro escenarios.
10. **Roles:** en la colectiva el tomador es la inmobiliaria o administración (en `Poliza`), y cada riesgo tiene su
    arrendatario (asegurado) y su arrendador (beneficiario). En la individual el tomador y el asegurado son el mismo
    arrendatario; `Poliza.individual` lo garantiza.
11. **Renovación automática y notificaciones por correo y SMS** están fuera del alcance de este módulo; se describen
    en el diseño del sistema (módulo 1). El evento `PolizaModificada` es el punto donde se conectarían.

## Decisiones de diseño

- **Capas** (`controller`, `service`, `repository`, `domain`, `dto`, `exception`, `config`) y **puerto y adaptador**
  solo para el CORE, que es la dependencia externa y la más cambiante.
- **`Poliza` es la raíz del agregado:** los riesgos solo se agregan o cancelan a través de ella, la lista que expone
  es de solo lectura y no hay setters. Las invariantes no dependen de que el service las recuerde.
- **DTO como `record`** y `open-in-view: false`: ninguna entidad sale de la transacción ni del service.
- **Datos semilla con `ApplicationRunner`** en lugar de `data.sql`: pasan por el dominio y los IDs los asigna la base.
- **Spring Boot 4.1 y Java 17:** la línea 3.x dejó de recibir parches gratuitos en junio de 2026; Java 17 es el
  mínimo que exige Boot 4.

## Qué haría distinto en producción

- **Outbox transaccional:** guardar el evento en una tabla dentro de la misma transacción y publicarlo con un proceso
  aparte, con reintentos con backoff, circuit breaker (Resilience4j), cola de mensajes fallidos e idempotencia en el
  CORE. Hoy, si el servidor cae justo después del commit, la notificación se pierde.
- **Renovación automática:** proceso programado que seleccione las pólizas por vencer, con bloqueo distribuido
  (por ejemplo, ShedLock) para que varias instancias no renueven la misma póliza.
- **Historial de vigencias:** una tabla con el canon anterior, el IPC aplicado y las fechas de cada renovación.
- **Oracle con migraciones versionadas** (Flyway o Liquibase) en lugar de H2 y `ddl-auto`.
- **Seguridad:** OAuth2/JWT en el API Gateway en lugar de una API key fija.
- **Versionamiento y contrato:** rutas `/v1` y especificación OpenAPI publicada.
- **Observabilidad:** logs JSON con correlationId, métricas con Micrometer (renovaciones, latencia y errores del CORE)
  y trazas distribuidas con OpenTelemetry.
- **Paginación** en `GET /polizas` y empaquetado con Docker.

## Estructura

```
src/main/java/com/jsierra/polizas
├── controller/        PolizaController, RiesgoController
├── service/           PolizaService, RiesgoService, PolizaModificada (evento)
├── repository/        PolizaRepository, RiesgoRepository
├── domain/            Poliza, Riesgo y enums
├── dto/               records de request y response
├── exception/         GlobalExceptionHandler, RespuestaError y excepciones de negocio
├── config/            ApiKeyFilter, CargaDatosSemilla
├── integration/core/  CorePort, CoreHttpAdapter, NotificadorCore
└── coremock/          CoreMockController
```
