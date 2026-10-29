package gt.usac.bd2.pruebas.consultas.mongo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.bson.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Lee al iniciar todas las consultas de resources/queries/mongo. Agregar o
 * modificar una consulta es editar su archivo JSON, sin tocar código Java.
 */
@Component
public class CatalogoConsultasMongo {

	private static final String UBICACION = "classpath:queries/mongo/q*.json";

	private final SortedMap<Integer, ConsultaMongo> consultas = new TreeMap<>();

	public CatalogoConsultasMongo() throws IOException {
		for (Resource archivo : new PathMatchingResourcePatternResolver().getResources(UBICACION)) {
			ConsultaMongo consulta = leer(archivo);
			if (consultas.putIfAbsent(consulta.id(), consulta) != null) {
				throw new IllegalStateException("Hay dos consultas de MongoDB con el id " + consulta.id());
			}
		}
	}

	public Collection<ConsultaMongo> todas() {
		return consultas.values();
	}

	public Optional<ConsultaMongo> buscar(int id) {
		return Optional.ofNullable(consultas.get(id));
	}

	private static ConsultaMongo leer(Resource archivo) {
		try (InputStream entrada = archivo.getInputStream()) {
			String json = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
			Document doc = Document.parse(json);

			List<ConsultaMongo.Parametro> parametros = new ArrayList<>();
			for (Document p : doc.getList("parametros", Document.class, List.of())) {
				parametros.add(new ConsultaMongo.Parametro(p.getString("nombre"), p.getString("tipo"),
						p.getBoolean("requerido", false), p.getString("descripcion")));
			}

			return new ConsultaMongo(doc.getInteger("id"), doc.getString("titulo"), doc.getString("descripcion"),
					doc.getString("coleccion"), doc.getString("coleccionResultado"), parametros, json);
		}
		catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer la consulta " + archivo.getFilename(), e);
		}
	}

}
