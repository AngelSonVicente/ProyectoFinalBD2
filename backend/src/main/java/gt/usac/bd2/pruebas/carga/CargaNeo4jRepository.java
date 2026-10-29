package gt.usac.bd2.pruebas.carga;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import gt.usac.bd2.pruebas.etl.ReglasLimpieza;
import gt.usac.bd2.pruebas.etl.RegistroEvaluacion;

/**
 * Genera los nodos y relaciones del grafo a partir de las evaluaciones.
 *
 * <pre>
 * (:Aspirante)-[:REALIZO {id, fecha, intento, anio_ingreso, aprobado, edad}]->(:Materia)
 * (:Aspirante)-[:ASPIRA_A]->(:Carrera)
 * (:Aspirante)-[:PROCEDE_DE]->(:Institucion)-[:UBICADA_EN]->(:Municipio)-[:PERTENECE_A]->(:Departamento)
 * </pre>
 *
 * Todo se hace con MERGE sobre propiedades únicas, así que volver a cargar
 * un archivo no crea nodos ni relaciones duplicadas.
 */
@Repository
public class CargaNeo4jRepository {

	/**
	 * El dataset no trae el nombre de la institución educativa, solo su tipo y
	 * ubicación. Por eso una Institucion se identifica por tipo + municipio.
	 * El municipio se identifica junto con su departamento porque hay
	 * municipios con el mismo nombre en departamentos distintos.
	 */
	private static final String CARGAR_LOTE = """
			UNWIND $filas AS f
			MERGE (a:Aspirante {id: f.correlativo})
			SET a.sexo = coalesce(f.sexo, a.sexo),
			    a.anio_nacimiento = coalesce(f.anio_nacimiento, a.anio_nacimiento)
			MERGE (m:Materia {nombre: f.materia})
			MERGE (c:Carrera {nombre: f.carrera})
			MERGE (d:Departamento {nombre: f.departamento})
			MERGE (mu:Municipio {clave: f.departamento + '|' + f.municipio})
			  ON CREATE SET mu.nombre = f.municipio
			MERGE (mu)-[:PERTENECE_A]->(d)
			MERGE (i:Institucion {clave: f.tipo + '|' + f.departamento + '|' + f.municipio})
			  ON CREATE SET i.tipo = f.tipo, i.municipio = f.municipio
			MERGE (i)-[:UBICADA_EN]->(mu)
			MERGE (a)-[:ASPIRA_A]->(c)
			MERGE (a)-[:PROCEDE_DE]->(i)
			MERGE (a)-[r:REALIZO {id: f.evaluacion_id}]->(m)
			SET r.fecha = f.fecha,
			    r.mes = f.mes,
			    r.intento = f.intento,
			    r.anio_ingreso = f.anio_ingreso,
			    r.aprobado = f.aprobado,
			    r.edad = f.edad
			""";

	private final Neo4jClient neo4jClient;

	public CargaNeo4jRepository(Neo4jClient neo4jClient) {
		this.neo4jClient = neo4jClient;
	}

	public void guardarEvaluaciones(List<RegistroEvaluacion> lote) {
		if (lote.isEmpty()) {
			return;
		}
		List<Map<String, Object>> filas = lote.stream().map(CargaNeo4jRepository::aParametros).toList();
		neo4jClient.query(CARGAR_LOTE).bind(filas).to("filas").run();
	}

	private static Map<String, Object> aParametros(RegistroEvaluacion r) {
		// HashMap porque Map.of no admite valores null (edad, año de nacimiento)
		Map<String, Object> f = new HashMap<>();
		f.put("evaluacion_id", MapeoDocumentos.idEvaluacion(r));
		f.put("correlativo", r.correlativoAspirante());
		// null hace que coalesce conserve el sexo ya conocido del aspirante
		f.put("sexo", ReglasLimpieza.SIN_REGISTRO.equals(r.sexo()) ? null : r.sexo());
		f.put("anio_nacimiento", r.anioNacimiento());
		f.put("materia", r.materia());
		f.put("carrera", r.carreraObjetivo());
		f.put("departamento", r.departamento());
		f.put("municipio", r.municipio());
		f.put("tipo", r.tipoInstitucion());
		f.put("fecha", r.fechaAsignacion());
		f.put("mes", r.mesAsignacion());
		f.put("intento", r.intento());
		f.put("anio_ingreso", r.anioIngreso());
		f.put("aprobado", r.aprobado());
		f.put("edad", r.edad());
		return f;
	}

}
