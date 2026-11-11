package com.jsierra.polizas.coremock;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Contrato del mock. No comparte clases con el adaptador: simula un sistema externo. */
public record EventoCoreMockRequest(@NotBlank String evento, @NotNull Long polizaId) {
}
