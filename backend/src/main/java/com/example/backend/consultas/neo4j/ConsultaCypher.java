package com.example.backend.consultas.neo4j;

import java.util.List;

/**
 * Consulta leída de resources/queries/cypher/cNN.cypher. Los metadatos van en
 * comentarios al inicio del archivo, así que el mismo archivo se puede pegar
 * tal cual en Neo4j Browser.
 *
 * @param cypher texto completo del archivo
 */
public record ConsultaCypher(int id, String titulo, String descripcion, List<Parametro> parametros, String cypher) {

	/**
	 * Parámetro que se recibe por query string y se envía a Neo4j como
	 * $nombre. Los parámetros opcionales sin valor se envían como null, por eso
	 * los filtros se escriben como ($x IS NULL OR ... = $x).
	 *
	 * @param tipo           correlativo (se pasa a minúsculas), entero o texto (se normaliza como en el ETL)
	 * @param valorPorDefecto valor cuando no se recibe el parámetro; null si no tiene
	 */
	public record Parametro(String nombre, String tipo, boolean requerido, String valorPorDefecto,
			String descripcion) {
	}

}
