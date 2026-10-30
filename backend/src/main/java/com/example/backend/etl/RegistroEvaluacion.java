package com.example.backend.etl;

import java.time.LocalDate;

/**
 * Una fila del CSV ya validada y transformada. Representa una evaluación
 * (un intento de un aspirante en una materia), no un aspirante.
 *
 * @param anioNacimiento null cuando el archivo trae "SIN REGISTRO" o un valor inválido
 * @param edad           anioIngreso - anioNacimiento; null si no se puede calcular o está fuera de rango
 */
public record RegistroEvaluacion(
		long numeroFila,
		String correlativoAspirante,
		LocalDate fechaAsignacion,
		int anioAsignacion,
		int mesAsignacion,
		String sexo,
		Integer anioNacimiento,
		Integer edad,
		String materia,
		int intento,
		int anioIngreso,
		boolean aprobado,
		String carreraObjetivo,
		String departamento,
		String municipio,
		String tipoInstitucion) {
}
