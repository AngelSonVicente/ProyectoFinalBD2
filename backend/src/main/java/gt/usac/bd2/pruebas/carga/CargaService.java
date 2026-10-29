package gt.usac.bd2.pruebas.carga;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.bson.types.ObjectId;
import org.neo4j.driver.exceptions.Neo4jException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mongodb.MongoException;

import gt.usac.bd2.pruebas.etl.EtlException;
import gt.usac.bd2.pruebas.etl.LectorCsv;
import gt.usac.bd2.pruebas.etl.RegistroEvaluacion;
import gt.usac.bd2.pruebas.etl.RegistroRechazado;

/**
 * Orquesta la carga de archivos: pasa cada CSV por el ETL y guarda los
 * resultados por lotes en MongoDB y en Neo4j. Cada archivo se procesa de
 * forma independiente, así que un archivo con errores no detiene la carga de
 * los demás.
 *
 * <p>Si una de las bases falla a mitad de un archivo, la carga queda FALLIDA
 * y lo ya guardado se conserva. Como ambas escrituras son idempotentes, basta
 * con volver a cargar el archivo para completarla.
 */
@Service
public class CargaService {

	private static final Logger log = LoggerFactory.getLogger(CargaService.class);

	private final LectorCsv lectorCsv;

	private final CargaMongoRepository repositorio;

	private final CargaNeo4jRepository grafo;

	private final int tamanoLote;

	public CargaService(LectorCsv lectorCsv, CargaMongoRepository repositorio, CargaNeo4jRepository grafo,
			@Value("${app.carga.tamano-lote:1000}") int tamanoLote) {
		this.lectorCsv = lectorCsv;
		this.repositorio = repositorio;
		this.grafo = grafo;
		this.tamanoLote = tamanoLote;
	}

	public List<ResumenCarga> cargar(List<MultipartFile> archivos) {
		return archivos.stream().map(this::cargarArchivo).toList();
	}

	ResumenCarga cargarArchivo(MultipartFile archivo) {
		String nombre = archivo.getOriginalFilename() == null ? "sin_nombre.csv" : archivo.getOriginalFilename();

		String problema = validarArchivo(archivo, nombre);
		if (problema != null) {
			return new ResumenCarga(null, nombre, 0, 0, 0, 0, 0, EstadoCarga.FALLIDO, problema);
		}

		try (InputStream entrada = archivo.getInputStream()) {
			return cargar(entrada, nombre);
		}
		catch (IOException e) {
			return new ResumenCarga(null, nombre, 0, 0, 0, 0, 0, EstadoCarga.FALLIDO,
					"No se pudo leer el archivo: " + e.getMessage());
		}
	}

	/**
	 * Procesa un CSV ya abierto. Separado de {@link #cargarArchivo} para poder
	 * probarlo sin una solicitud HTTP.
	 */
	ResumenCarga cargar(InputStream entrada, String nombre) {
		long inicio = System.nanoTime();
		ObjectId cargaId = new ObjectId();
		Progreso progreso = new Progreso(cargaId, nombre);

		try {
			repositorio.registrarInicio(cargaId, nombre, new Date());
		}
		catch (MongoException e) {
			log.error("MongoDB no disponible al iniciar la carga de {}", nombre, e);
			return new ResumenCarga(null, nombre, 0, 0, 0, 0, 0, EstadoCarga.FALLIDO,
					"No se pudo conectar con MongoDB: " + e.getMessage());
		}

		EstadoCarga estado;
		String mensaje = null;
		try {
			lectorCsv.procesar(entrada, nombre, progreso::agregarValido, progreso::agregarRechazado);
			progreso.vaciarLotes();
			estado = progreso.rechazados > 0 ? EstadoCarga.COMPLETADO_CON_RECHAZOS : EstadoCarga.COMPLETADO;
		}
		catch (EtlException e) {
			estado = EstadoCarga.FALLIDO;
			mensaje = e.getMessage();
		}
		catch (MongoException e) {
			log.error("Error de MongoDB durante la carga de {}", nombre, e);
			estado = EstadoCarga.FALLIDO;
			mensaje = "Error al guardar en MongoDB: " + e.getMessage();
		}
		catch (DataAccessException | Neo4jException e) {
			log.error("Error de Neo4j durante la carga de {}", nombre, e);
			estado = EstadoCarga.FALLIDO;
			mensaje = "Error al guardar en Neo4j: " + e.getMessage();
		}

		long tiempoMs = (System.nanoTime() - inicio) / 1_000_000;
		ResumenCarga resumen = new ResumenCarga(cargaId.toHexString(), nombre, progreso.validos + progreso.rechazados,
				progreso.guardados, progreso.rechazados, progreso.duplicados, tiempoMs, estado, mensaje);

		try {
			repositorio.registrarFin(resumen, new Date());
		}
		catch (MongoException e) {
			log.error("No se pudo registrar el fin de la carga {}", cargaId, e);
		}
		log.info("Carga {} de {}: {} leídos, {} procesados, {} rechazados en {} ms", estado, nombre, resumen.leidos(),
				resumen.procesados(), resumen.rechazados(), tiempoMs);
		return resumen;
	}

	private static String validarArchivo(MultipartFile archivo, String nombre) {
		if (archivo.isEmpty()) {
			return "El archivo está vacío";
		}
		if (!nombre.toLowerCase(Locale.ROOT).endsWith(".csv")) {
			return "Solo se aceptan archivos .csv";
		}
		return null;
	}

	/**
	 * Acumula los registros que va entregando el ETL y los guarda cuando el
	 * lote se llena, para no tener todo el archivo en memoria.
	 */
	private final class Progreso {

		private final ObjectId cargaId;

		private final String archivo;

		private final List<RegistroEvaluacion> loteValidos = new ArrayList<>(tamanoLote);

		private final List<RegistroRechazado> loteRechazados = new ArrayList<>();

		/** Filas válidas leídas, se hayan guardado o no. */
		private long validos;

		/** Filas válidas ya guardadas en MongoDB y en Neo4j. */
		private long guardados;

		private long rechazados;

		private long duplicados;

		private Progreso(ObjectId cargaId, String archivo) {
			this.cargaId = cargaId;
			this.archivo = archivo;
		}

		private void agregarValido(RegistroEvaluacion registro) {
			loteValidos.add(registro);
			validos++;
			if (loteValidos.size() >= tamanoLote) {
				guardarValidos();
			}
		}

		private void agregarRechazado(RegistroRechazado registro) {
			loteRechazados.add(registro);
			rechazados++;
			if (loteRechazados.size() >= tamanoLote) {
				guardarRechazados();
			}
		}

		private void vaciarLotes() {
			guardarValidos();
			guardarRechazados();
		}

		private void guardarValidos() {
			long duplicadosLote = repositorio.guardarEvaluaciones(loteValidos, cargaId, archivo);
			grafo.guardarEvaluaciones(loteValidos);
			// Solo se cuentan cuando el lote quedó en ambas bases
			duplicados += duplicadosLote;
			guardados += loteValidos.size();
			loteValidos.clear();
		}

		private void guardarRechazados() {
			repositorio.guardarRechazados(loteRechazados, cargaId, archivo);
			loteRechazados.clear();
		}

	}

}
