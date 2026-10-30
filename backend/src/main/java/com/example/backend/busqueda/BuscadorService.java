package com.example.backend.busqueda;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

import com.example.backend.config.Colecciones;
import com.example.backend.etl.ReglasLimpieza;

/**
 * Busca evaluaciones en MongoDB combinando los filtros recibidos. Los textos
 * se normalizan igual que en el ETL, así que "mixco" encuentra "MIXCO" y
 * "ingenieria" encuentra "INGENIERÍA".
 *
 * Los errores de MongoDB no se capturan aquí: los convierte en una respuesta
 * controlada el manejador global de errores.
 */
@Service
public class BuscadorService {

	private final MongoTemplate mongoTemplate;

	public BuscadorService(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public record PaginaBusqueda(long total, int pagina, int tamano, long totalPaginas,
			Map<String, Object> filtros, long tiempoMs, List<Document> datos) {
	}

	public record Opciones(List<String> carreras, List<String> materias, List<String> departamentos,
			List<Document> municipios, List<String> tiposInstitucion, List<Integer> aniosIngreso) {
	}

	public PaginaBusqueda buscar(FiltrosBusqueda filtros, int pagina, int tamano) {
		Map<String, Object> aplicados = new LinkedHashMap<>();
		List<Bson> condiciones = new ArrayList<>();

		if (tieneTexto(filtros.aspirante())) {
			// Mismo formato que guarda el ETL. "Empieza con" aprovecha el índice
			String correlativo = ReglasLimpieza.aCorrelativo(filtros.aspirante());
			condiciones.add(Filters.regex("correlativo_aspirante", "^" + escaparRegex(correlativo)));
			aplicados.put("aspirante", correlativo);
		}
		contiene(condiciones, aplicados, "carrera", "carrera_objetivo", filtros.carrera());
		contiene(condiciones, aplicados, "institucion", "tipo_institucion", filtros.institucion());
		contiene(condiciones, aplicados, "materia", "materia", filtros.materia());
		contiene(condiciones, aplicados, "municipio", "municipio", filtros.municipio());
		contiene(condiciones, aplicados, "departamento", "departamento", filtros.departamento());
		if (filtros.anio() != null) {
			condiciones.add(Filters.eq("anio_ingreso", filtros.anio()));
			aplicados.put("anio", filtros.anio());
		}
		if (filtros.aprobado() != null) {
			condiciones.add(Filters.eq("aprobado", filtros.aprobado()));
			aplicados.put("aprobado", filtros.aprobado());
		}

		MongoCollection<Document> evaluaciones = mongoTemplate.getCollection(Colecciones.EVALUACIONES);
		Bson filtro = condiciones.isEmpty() ? new Document() : Filters.and(condiciones);

		long inicio = System.nanoTime();
		// Sin filtros, contar toda la colección es mucho más rápido con el estimado
		long total = condiciones.isEmpty() ? evaluaciones.estimatedDocumentCount() : evaluaciones.countDocuments(filtro);
		// El _id empieza con correlativo|materia|fecha, así que ordenar por _id agrupa
		// las evaluaciones de cada aspirante y usa el índice de _id
		List<Document> datos = evaluaciones.find(filtro)
				.projection(Projections.exclude("carga_id"))
				.sort(Sorts.ascending("_id"))
				.skip(pagina * tamano)
				.limit(tamano)
				.into(new ArrayList<>());
		long tiempoMs = (System.nanoTime() - inicio) / 1_000_000;

		long totalPaginas = (total + tamano - 1) / tamano;
		return new PaginaBusqueda(total, pagina, tamano, totalPaginas, aplicados, tiempoMs, datos);
	}

	/** Valores existentes de cada criterio, para llenar las listas desplegables del frontend. */
	public Opciones opciones() {
		MongoCollection<Document> evaluaciones = mongoTemplate.getCollection(Colecciones.EVALUACIONES);
		List<Document> municipios = evaluaciones.aggregate(List.of(
				Aggregates.group(new Document("departamento", "$departamento").append("municipio", "$municipio"),
						Accumulators.sum("evaluaciones", 1)),
				Aggregates.project(Projections.fields(Projections.excludeId(),
						Projections.computed("departamento", "$_id.departamento"),
						Projections.computed("municipio", "$_id.municipio"))),
				Aggregates.sort(Sorts.ascending("departamento", "municipio"))))
				.into(new ArrayList<>());

		return new Opciones(distintos(evaluaciones, "carrera_objetivo", String.class),
				distintos(evaluaciones, "materia", String.class), distintos(evaluaciones, "departamento", String.class),
				municipios, distintos(evaluaciones, "tipo_institucion", String.class),
				distintos(evaluaciones, "anio_ingreso", Integer.class));
	}

	private static <T extends Comparable<T>> List<T> distintos(MongoCollection<Document> coleccion, String campo,
			Class<T> tipo) {
		List<T> valores = coleccion.distinct(campo, tipo).into(new ArrayList<>());
		valores.removeIf(v -> v == null);
		valores.sort(null);
		return valores;
	}

	private static void contiene(List<Bson> condiciones, Map<String, Object> aplicados, String filtro, String campo,
			String valor) {
		if (!tieneTexto(valor)) {
			return;
		}
		String normalizado = ReglasLimpieza.normalizarTexto(valor);
		condiciones.add(Filters.regex(campo, escaparRegex(normalizado)));
		aplicados.put(filtro, normalizado);
	}

	private static boolean tieneTexto(String valor) {
		return valor != null && !valor.isBlank();
	}

	/**
	 * Escapa los caracteres especiales para que el texto del usuario se busque
	 * literalmente y no se interprete como expresión regular.
	 */
	static String escaparRegex(String texto) {
		StringBuilder escapado = new StringBuilder(texto.length());
		for (char c : texto.toCharArray()) {
			if ("\\^$.|?*+()[]{}-/".indexOf(c) >= 0) {
				escapado.append('\\');
			}
			escapado.append(c);
		}
		return escapado.toString();
	}

}
