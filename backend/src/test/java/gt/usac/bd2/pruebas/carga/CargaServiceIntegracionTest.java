package gt.usac.bd2.pruebas.carga;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;

import gt.usac.bd2.pruebas.config.Colecciones;

/**
 * Requiere el MongoDB de docker-compose. Usa la base pruebas_test para no
 * tocar los datos reales.
 */
@SpringBootTest(properties = {
		"spring.mongodb.uri=mongodb://admin:admin123@localhost:27017/pruebas_test?authSource=admin",
		"app.carga.tamano-lote=2" })
class CargaServiceIntegracionTest {

	private static final String ENCABEZADO = "fecha_asignacion,sexo,anio_nacimiento,materia,numero_de_fecha_de_evaluacion,"
			+ "anio_de_ingreso,aprobacion,carrera_objetivo,departamento_institucion_educativa,"
			+ "municipio_institucion_educativa,tipo_institucion_educativa,correlativo_aspirante\n";

	// 2 aspirantes, 3 evaluaciones válidas y 1 fila inválida
	private static final String CSV = ENCABEZADO
			+ "2014-08-23,M,1998,MATEMATICA,1,2015,NO APROBADO,INGENIERIA CIVIL,GUATEMALA,MIXCO,PUBLICO,aaa\n"
			+ "2014-10-11,SIN REGISTRO,SIN REGISTRO,MATEMATICA,2,2015,APROBADO,INGENIERIA CIVIL,GUATEMALA,MIXCO,PUBLICO,aaa\n"
			+ "2014-08-23,F,1997,FISICA,1,2015,APROBADO,NINGUNO,ESCUINTLA,ESCUINTLA,PRIVADO,bbb\n"
			+ "fecha-mala,F,1997,FISICA,1,2015,QUIZAS,NINGUNO,ESCUINTLA,ESCUINTLA,PRIVADO,bbb\n";

	@Autowired
	private CargaService cargaService;

	@Autowired
	private MongoTemplate mongoTemplate;

	@BeforeEach
	void limpiar() {
		mongoTemplate.getDb().drop();
	}

	private ResumenCarga cargar(String contenido) {
		return cargaService.cargar(new ByteArrayInputStream(contenido.getBytes(StandardCharsets.UTF_8)), "prueba.csv");
	}

	@Test
	void guardaEvaluacionesAspirantesYRechazados() {
		ResumenCarga resumen = cargar(CSV);

		assertThat(resumen.estado()).isEqualTo(EstadoCarga.COMPLETADO_CON_RECHAZOS);
		assertThat(resumen.leidos()).isEqualTo(4);
		assertThat(resumen.procesados()).isEqualTo(3);
		assertThat(resumen.rechazados()).isEqualTo(1);
		assertThat(resumen.duplicados()).isZero();

		assertThat(contar(Colecciones.EVALUACIONES)).isEqualTo(3);
		assertThat(contar(Colecciones.ASPIRANTES)).isEqualTo(2);
		assertThat(contar(Colecciones.REGISTROS_RECHAZADOS)).isEqualTo(1);

		Document aspirante = mongoTemplate.getCollection(Colecciones.ASPIRANTES).find(new Document("_id", "aaa")).first();
		assertThat(aspirante.getList("evaluaciones", Document.class)).hasSize(2);
		assertThat(aspirante.getList("materias", String.class)).containsExactly("MATEMATICA");
		// La segunda fila trae SIN REGISTRO y no debe borrar los datos de la primera
		assertThat(aspirante.getString("sexo")).isEqualTo("M");
		assertThat(aspirante.getInteger("anio_nacimiento")).isEqualTo(1998);

		Document carga = mongoTemplate.getCollection(Colecciones.CARGAS)
				.find(new Document("_id", new ObjectId(resumen.id()))).first();
		assertThat(carga.getString("estado")).isEqualTo("COMPLETADO_CON_RECHAZOS");
		assertThat(carga.get("procesados", Number.class).longValue()).isEqualTo(3);
	}

	@Test
	void cargarElMismoArchivoDosVecesNoDuplicaEvaluaciones() {
		cargar(CSV);
		ResumenCarga segunda = cargar(CSV);

		assertThat(segunda.duplicados()).isEqualTo(3);
		assertThat(contar(Colecciones.EVALUACIONES)).isEqualTo(3);
		Document aspirante = mongoTemplate.getCollection(Colecciones.ASPIRANTES).find(new Document("_id", "aaa")).first();
		assertThat(aspirante.getList("evaluaciones", Document.class)).hasSize(2);
	}

	@Test
	void unArchivoInvalidoNoDetieneLosDemas() {
		List<ResumenCarga> resumenes = cargaService.cargar(List.of(
				new MockMultipartFile("archivos", "malo.csv", "text/csv", "sexo,materia\nM,FISICA\n".getBytes()),
				new MockMultipartFile("archivos", "datos.txt", "text/plain", "x".getBytes()),
				new MockMultipartFile("archivos", "bueno.csv", "text/csv", CSV.getBytes(StandardCharsets.UTF_8))));

		assertThat(resumenes).extracting(ResumenCarga::estado)
				.containsExactly(EstadoCarga.FALLIDO, EstadoCarga.FALLIDO, EstadoCarga.COMPLETADO_CON_RECHAZOS);
		assertThat(resumenes.get(0).mensaje()).contains("Faltan columnas");
		assertThat(resumenes.get(1).mensaje()).contains(".csv");
		assertThat(contar(Colecciones.EVALUACIONES)).isEqualTo(3);
	}

	private long contar(String coleccion) {
		return mongoTemplate.getCollection(coleccion).countDocuments();
	}

}
