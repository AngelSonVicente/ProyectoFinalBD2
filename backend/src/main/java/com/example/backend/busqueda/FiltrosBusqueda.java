package com.example.backend.busqueda;

/**
 * Criterios del buscador. Todos son opcionales y se combinan con AND.
 *
 * @param aspirante    inicio del correlativo del aspirante
 * @param carrera      parte del nombre de la carrera objetivo
 * @param institucion  parte del tipo de institución (el dataset no trae el nombre)
 * @param materia      parte del nombre de la materia
 * @param municipio    parte del nombre del municipio
 * @param departamento parte del nombre del departamento
 * @param anio         año de ingreso exacto
 * @param aprobado     resultado de la evaluación
 */
public record FiltrosBusqueda(String aspirante, String carrera, String institucion, String materia, String municipio,
		String departamento, Integer anio, Boolean aprobado) {
}
