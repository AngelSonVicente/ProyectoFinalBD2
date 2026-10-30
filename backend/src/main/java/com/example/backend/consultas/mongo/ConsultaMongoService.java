package com.example.backend.consultas.mongo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.mongodb.MongoException;

import com.example.backend.etl.ReglasLimpieza;

/**
 * Ejecuta las consultas del catálogo sobre MongoDB.
 */
@Service
public class ConsultaMongoService {

	private static final Pattern MARCADOR = Pattern.compile("^\\{\\{(\\w+)}}$");

	private final CatalogoConsultasMongo catalogo;

	private final MongoTemplate mongoTemplate;

	public ConsultaMongoService(CatalogoConsultasMongo catalogo, MongoTemplate mongoTemplate) {
		this.catalogo = catalogo;
		this.mongoTemplate = mongoTemplate;
	}

	public record ResultadoConsulta(int id, String titulo, String descripcion, String coleccion, int total,
			long tiempoMs, List<Document> datos) {
	}

	public ResultadoConsulta ejecutar(int id, Map<String, String> valores) {
		ConsultaMongo consulta = catalogo.buscar(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la consulta " + id));

		Map<String, Object> parametros = prepararParametros(consulta, valores);
		List<Document> pipeline = Document.parse(consulta.json()).getList("pipeline", Document.class);
		pipeline.forEach(etapa -> reemplazarMarcadores(etapa, parametros));

		long inicio = System.nanoTime();
		List<Document> datos;
		try {
			datos = mongoTemplate.getCollection(consulta.coleccion())
					.aggregate(pipeline)
					.allowDiskUse(true)
					.into(new ArrayList<>());

			// Con $out el pipeline no devuelve filas: se muestra la colección generada
			if (consulta.coleccionResultado() != null) {
				datos = mongoTemplate.getCollection(consulta.coleccionResultado()).find().into(new ArrayList<>());
			}
		}
		catch (MongoException e) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Error al ejecutar la consulta " + id + " en MongoDB: " + e.getMessage(), e);
		}
		long tiempoMs = (System.nanoTime() - inicio) / 1_000_000;

		return new ResultadoConsulta(id, consulta.titulo(), consulta.descripcion(), consulta.coleccion(), datos.size(),
				tiempoMs, datos);
	}

	/** Valida los parámetros recibidos y los convierte al tipo que espera el pipeline. */
	private static Map<String, Object> prepararParametros(ConsultaMongo consulta, Map<String, String> valores) {
		Map<String, Object> parametros = new HashMap<>();
		for (ConsultaMongo.Parametro p : consulta.parametros()) {
			String valor = valores.get(p.nombre());
			if (valor == null || valor.isBlank()) {
				if (p.requerido()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"La consulta " + consulta.id() + " requiere el parámetro '" + p.nombre() + "'");
				}
				continue;
			}
			parametros.put(p.nombre(), convertir(p, valor.trim()));
		}
		return parametros;
	}

	private static Object convertir(ConsultaMongo.Parametro p, String valor) {
		return switch (p.tipo()) {
			// El ETL guarda los correlativos en minúsculas
			// Igual que en el ETL: minúsculas y solo los primeros 20 caracteres
			case "correlativo" -> ReglasLimpieza.aCorrelativo(valor);
			case "entero" -> {
				try {
					yield Integer.parseInt(valor);
				}
				catch (NumberFormatException e) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"El parámetro '" + p.nombre() + "' debe ser un número entero");
				}
			}
			default -> ReglasLimpieza.normalizarTexto(valor);
		};
	}

	/**
	 * Reemplaza los valores "{{nombre}}" por el parámetro correspondiente. Se
	 * reemplaza el valor completo (no se arma texto), así que un parámetro
	 * nunca puede inyectar operadores en el pipeline.
	 */
	@SuppressWarnings("unchecked")
	private static Object reemplazarMarcadores(Object nodo, Map<String, Object> parametros) {
		if (nodo instanceof Map<?, ?> mapa) {
			((Map<String, Object>) mapa).replaceAll((clave, valor) -> reemplazarMarcadores(valor, parametros));
			return mapa;
		}
		if (nodo instanceof List<?> lista) {
			((List<Object>) lista).replaceAll(valor -> reemplazarMarcadores(valor, parametros));
			return lista;
		}
		if (nodo instanceof String texto) {
			Matcher m = MARCADOR.matcher(texto);
			if (m.matches()) {
				return parametros.get(m.group(1));
			}
		}
		return nodo;
	}

}
