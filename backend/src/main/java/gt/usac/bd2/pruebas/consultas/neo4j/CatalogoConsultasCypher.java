package gt.usac.bd2.pruebas.consultas.neo4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Lee al iniciar las consultas de resources/queries/cypher. Cada archivo
 * empieza con comentarios de metadatos:
 *
 * <pre>
 * // id: 1
 * // titulo: ...
 * // descripcion: ...
 * // parametro: nombre | tipo | requerido, valor por defecto o vacío | descripción
 * </pre>
 */
@Component
public class CatalogoConsultasCypher {

	private static final String UBICACION = "classpath:queries/cypher/c*.cypher";

	private static final String PREFIJO = "//";

	private final SortedMap<Integer, ConsultaCypher> consultas = new TreeMap<>();

	public CatalogoConsultasCypher() throws IOException {
		for (Resource archivo : new PathMatchingResourcePatternResolver().getResources(UBICACION)) {
			ConsultaCypher consulta = leer(archivo);
			if (consultas.putIfAbsent(consulta.id(), consulta) != null) {
				throw new IllegalStateException("Hay dos consultas Cypher con el id " + consulta.id());
			}
		}
	}

	public Collection<ConsultaCypher> todas() {
		return consultas.values();
	}

	public Optional<ConsultaCypher> buscar(int id) {
		return Optional.ofNullable(consultas.get(id));
	}

	private static ConsultaCypher leer(Resource archivo) {
		String cypher;
		try (InputStream entrada = archivo.getInputStream()) {
			cypher = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer la consulta " + archivo.getFilename(), e);
		}

		Map<String, String> metadatos = new HashMap<>();
		List<ConsultaCypher.Parametro> parametros = new ArrayList<>();
		for (String linea : cypher.lines().toList()) {
			if (!linea.startsWith(PREFIJO)) {
				break;
			}
			String[] claveValor = linea.substring(PREFIJO.length()).split(":", 2);
			if (claveValor.length < 2) {
				continue;
			}
			String clave = claveValor[0].trim();
			String valor = claveValor[1].trim();
			if (clave.equals("parametro")) {
				parametros.add(parametro(valor, archivo));
			}
			else {
				metadatos.put(clave, valor);
			}
		}

		if (!metadatos.containsKey("id") || !metadatos.containsKey("titulo")) {
			throw new IllegalStateException("La consulta " + archivo.getFilename() + " no tiene id o titulo");
		}
		return new ConsultaCypher(Integer.parseInt(metadatos.get("id")), metadatos.get("titulo"),
				metadatos.get("descripcion"), parametros, cypher);
	}

	private static ConsultaCypher.Parametro parametro(String definicion, Resource archivo) {
		String[] partes = definicion.split("\\|", -1);
		if (partes.length != 4) {
			throw new IllegalStateException("Parámetro mal definido en " + archivo.getFilename() + ": " + definicion);
		}
		String nombre = partes[0].trim();
		String tipo = partes[1].trim();
		String tercero = partes[2].trim();
		boolean requerido = tercero.equals("requerido");
		String porDefecto = requerido || tercero.isEmpty() ? null : tercero;
		return new ConsultaCypher.Parametro(nombre, tipo, requerido, porDefecto, partes[3].trim());
	}

}
