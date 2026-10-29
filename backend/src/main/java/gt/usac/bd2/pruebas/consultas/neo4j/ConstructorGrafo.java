package gt.usac.bd2.pruebas.consultas.neo4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;

/**
 * Convierte el resultado de una consulta Cypher al formato {nodes, links} que
 * usa la serie graph de ECharts, más una tabla con las columnas simples.
 *
 * <ul>
 * <li>Cada nodo, relación o camino que devuelva la consulta (también dentro de
 * listas) se agrega al grafo sin repetir.</li>
 * <li>Las columnas que no son nodos, relaciones ni caminos forman una fila de
 * la tabla.</li>
 * <li>Si una fila trae las columnas {@code origen} y {@code destino} como
 * nodos, se agrega un enlace calculado entre ellos con el tipo de la columna
 * {@code relacion} y las columnas simples como propiedades. Así una consulta
 * con agregación (por ejemplo, aspirantes por departamento y carrera) también
 * se puede dibujar.</li>
 * </ul>
 */
public class ConstructorGrafo {

	public record Nodo(String id, String nombre, String categoria, Map<String, Object> propiedades) {
	}

	public record Enlace(String id, String source, String target, String tipo, Map<String, Object> propiedades) {
	}

	private static final String ORIGEN = "origen";

	private static final String DESTINO = "destino";

	private static final String RELACION = "relacion";

	private final Map<String, Nodo> nodos = new LinkedHashMap<>();

	private final Map<String, Enlace> enlaces = new LinkedHashMap<>();

	private final List<Map<String, Object>> filas = new ArrayList<>();

	void agregar(Record registro) {
		Map<String, Object> fila = new LinkedHashMap<>();
		for (String columna : registro.keys()) {
			Object valor = registro.get(columna).asObject();
			if (!agregarElemento(valor)) {
				fila.put(columna, valor);
			}
		}

		Value origen = registro.containsKey(ORIGEN) ? registro.get(ORIGEN) : null;
		Value destino = registro.containsKey(DESTINO) ? registro.get(DESTINO) : null;
		if (origen != null && destino != null && origen.asObject() instanceof Node o
				&& destino.asObject() instanceof Node d) {
			Object relacion = fila.remove(RELACION);
			String tipo = relacion == null ? "RELACION" : relacion.toString();
			String id = o.elementId() + "-" + tipo + "-" + d.elementId();
			enlaces.putIfAbsent(id, new Enlace(id, o.elementId(), d.elementId(), tipo, new LinkedHashMap<>(fila)));
		}

		if (!fila.isEmpty()) {
			filas.add(fila);
		}
	}

	/** Devuelve true si el valor era parte del grafo (nodo, relación, camino o lista de ellos). */
	private boolean agregarElemento(Object valor) {
		switch (valor) {
			case Node nodo -> {
				nodos.computeIfAbsent(nodo.elementId(), id -> aNodo(nodo));
				return true;
			}
			case Relationship relacion -> {
				enlaces.computeIfAbsent(relacion.elementId(), id -> aEnlace(relacion));
				return true;
			}
			case Path camino -> {
				camino.nodes().forEach(this::agregarElemento);
				camino.relationships().forEach(this::agregarElemento);
				return true;
			}
			case List<?> lista when !lista.isEmpty() && lista.stream().allMatch(ConstructorGrafo::esDelGrafo) -> {
				lista.forEach(this::agregarElemento);
				return true;
			}
			case null, default -> {
				return false;
			}
		}
	}

	private static boolean esDelGrafo(Object valor) {
		return valor instanceof Node || valor instanceof Relationship || valor instanceof Path;
	}

	List<Nodo> nodos() {
		return List.copyOf(nodos.values());
	}

	/** Solo los enlaces cuyos dos extremos están en el grafo, para que ECharts no falle. */
	List<Enlace> enlaces() {
		return enlaces.values().stream()
				.filter(e -> nodos.containsKey(e.source()) && nodos.containsKey(e.target()))
				.toList();
	}

	List<Map<String, Object>> filas() {
		return filas;
	}

	private static Nodo aNodo(Node nodo) {
		String categoria = nodo.labels().iterator().hasNext() ? nodo.labels().iterator().next() : "Nodo";
		Map<String, Object> propiedades = nodo.asMap();
		return new Nodo(nodo.elementId(), nombre(categoria, propiedades), categoria, propiedades);
	}

	private static String nombre(String categoria, Map<String, Object> p) {
		return switch (categoria) {
			case "Aspirante" -> String.valueOf(p.get("id"));
			// La institución no tiene nombre en el dataset: se identifica por tipo y municipio
			case "Institucion" -> p.get("tipo") + " (" + p.get("municipio") + ")";
			default -> String.valueOf(p.getOrDefault("nombre", categoria));
		};
	}

	private static Enlace aEnlace(Relationship relacion) {
		return new Enlace(relacion.elementId(), relacion.startNodeElementId(), relacion.endNodeElementId(),
				relacion.type(), relacion.asMap());
	}

}
