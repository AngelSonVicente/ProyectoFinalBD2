package com.example.backend.consultas.neo4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.backend.etl.ReglasLimpieza;

/**
 * Ejecuta las consultas Cypher del catálogo y arma la respuesta para el
 * explorador de grafos.
 */
@Service
public class ConsultaCypherService {

	/** Tope para cualquier parámetro "limite", para que el navegador pueda dibujar el grafo. */
	static final int LIMITE_MAXIMO = 500;

	private final CatalogoConsultasCypher catalogo;

	private final Neo4jClient neo4jClient;

	public ConsultaCypherService(CatalogoConsultasCypher catalogo, Neo4jClient neo4jClient) {
		this.catalogo = catalogo;
		this.neo4jClient = neo4jClient;
	}

	public record ResultadoGrafo(int id, String titulo, String descripcion, Map<String, Object> parametros,
			long tiempoMs, List<ConstructorGrafo.Nodo> nodes, List<ConstructorGrafo.Enlace> links,
			List<Map<String, Object>> filas) {
	}

	public ResultadoGrafo ejecutar(int id, Map<String, String> valores) {
		ConsultaCypher consulta = catalogo.buscar(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la consulta Cypher " + id));

		Map<String, Object> parametros = prepararParametros(consulta, valores);
		ConstructorGrafo grafo = new ConstructorGrafo();

		long inicio = System.nanoTime();
		try {
			// Los valores viajan como parámetros de Cypher ($nombre), nunca concatenados en el texto
			neo4jClient.query(consulta.cypher())
					.bindAll(parametros)
					.fetchAs(Object.class)
					.mappedBy((tipos, registro) -> {
						grafo.agregar(registro);
						return Boolean.TRUE;
					})
					.all();
		}
		catch (DataAccessException | Neo4jException e) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Error al ejecutar la consulta Cypher " + id + " en Neo4j: " + e.getMessage(), e);
		}
		long tiempoMs = (System.nanoTime() - inicio) / 1_000_000;

		return new ResultadoGrafo(id, consulta.titulo(), consulta.descripcion(), parametros, tiempoMs, grafo.nodos(),
				grafo.enlaces(), grafo.filas());
	}

	/**
	 * Valida los parámetros y los convierte al tipo que espera la consulta. Todos
	 * los parámetros declarados se envían; los opcionales sin valor van como null.
	 */
	private static Map<String, Object> prepararParametros(ConsultaCypher consulta, Map<String, String> valores) {
		// HashMap porque Map.of no admite valores null
		Map<String, Object> parametros = new HashMap<>();
		for (ConsultaCypher.Parametro p : consulta.parametros()) {
			String valor = valores.get(p.nombre());
			if (valor == null || valor.isBlank()) {
				valor = p.valorPorDefecto();
			}
			if (valor == null) {
				if (p.requerido()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"La consulta Cypher " + consulta.id() + " requiere el parámetro '" + p.nombre() + "'");
				}
				parametros.put(p.nombre(), null);
				continue;
			}
			parametros.put(p.nombre(), convertir(p, valor.trim()));
		}
		return parametros;
	}

	private static Object convertir(ConsultaCypher.Parametro p, String valor) {
		return switch (p.tipo()) {
			// El ETL guarda los correlativos en minúsculas
			// Igual que en el ETL: minúsculas y solo los primeros 20 caracteres
			case "correlativo" -> ReglasLimpieza.aCorrelativo(valor);
			case "entero" -> {
				long numero;
				try {
					numero = Long.parseLong(valor);
				}
				catch (NumberFormatException e) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"El parámetro '" + p.nombre() + "' debe ser un número entero");
				}
				if (numero < 1 || numero > LIMITE_MAXIMO) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"El parámetro '" + p.nombre() + "' debe estar entre 1 y " + LIMITE_MAXIMO);
				}
				yield numero;
			}
			default -> ReglasLimpieza.normalizarTexto(valor);
		};
	}

}
