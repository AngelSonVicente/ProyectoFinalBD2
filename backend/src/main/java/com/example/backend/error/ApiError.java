package com.example.backend.error;

import java.time.Instant;

/**
 * Respuesta de error de toda la API. El frontend muestra {@code error} al
 * usuario; {@code detalle} trae la causa técnica cuando la hay.
 */
public record ApiError(int estado, String error, String detalle, String ruta, Instant timestamp) {
}
