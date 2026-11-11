package com.jsierra.polizas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body de POST /polizas/{id}/riesgos. */
public record RiesgoRequest(
        @NotBlank @Size(max = 150) String arrendatario,
        @NotBlank @Size(max = 150) String arrendador,
        @NotBlank @Size(max = 200) String direccionInmueble) {
}
