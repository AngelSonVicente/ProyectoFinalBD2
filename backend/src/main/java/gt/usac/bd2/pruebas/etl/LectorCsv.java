package gt.usac.bd2.pruebas.etl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Lee un CSV del dataset fila por fila (sin cargarlo completo en memoria),
 * lo pasa por el {@link TransformadorRegistro} y entrega cada resultado a
 * quien lo vaya a almacenar.
 */
@Component
public class LectorCsv {

	private final TransformadorRegistro transformador;

	public LectorCsv(TransformadorRegistro transformador) {
		this.transformador = transformador;
	}

	/**
	 * @param alValido    recibe cada registro válido (por ejemplo, para insertarlo por lotes)
	 * @param alRechazado recibe cada registro rechazado con su causa
	 * @throws EtlException si el archivo no se puede leer o le faltan columnas
	 */
	public ResultadoEtl procesar(InputStream entrada, String nombreArchivo,
			Consumer<RegistroEvaluacion> alValido, Consumer<RegistroRechazado> alRechazado) {
		long inicio = System.nanoTime();
		long leidos = 0;
		long validos = 0;
		long rechazados = 0;

		try (BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
			char delimitador = detectarDelimitador(lector);
			CSVFormat formato = CSVFormat.DEFAULT.builder()
					.setDelimiter(delimitador)
					.setHeader()
					.setSkipHeaderRecord(true)
					.setIgnoreEmptyLines(true)
					.setTrim(true)
					.get();

			try (CSVParser parser = CSVParser.parse(lector, formato)) {
				Map<String, String> columnas = mapearColumnas(parser.getHeaderNames());

				for (CSVRecord fila : parser) {
					leidos++;
					// +1 porque la fila 1 del archivo es el encabezado
					long numeroFila = fila.getRecordNumber() + 1;
					TransformadorRegistro.Resultado resultado = fila.isConsistent()
							? transformador.transformar(aMapa(fila, columnas), numeroFila)
							: filaInconsistente(fila, columnas, parser.getHeaderNames().size(), numeroFila);

					switch (resultado) {
						case TransformadorRegistro.Valido v -> {
							validos++;
							alValido.accept(v.registro());
						}
						case TransformadorRegistro.Rechazado r -> {
							rechazados++;
							alRechazado.accept(r.registro());
						}
					}
				}
			}
		}
		catch (IOException | UncheckedIOException e) {
			throw new EtlException("No se pudo leer el archivo " + nombreArchivo + ": " + e.getMessage(), e);
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			// Commons CSV lanza estas excepciones con encabezados duplicados o comillas mal cerradas
			throw new EtlException("Formato CSV inválido en " + nombreArchivo + ": " + e.getMessage(), e);
		}

		long tiempoMs = (System.nanoTime() - inicio) / 1_000_000;
		return new ResultadoEtl(nombreArchivo, leidos, validos, rechazados, tiempoMs);
	}

	/**
	 * Mira la primera línea sin consumirla para saber si el archivo usa coma
	 * o punto y coma (Excel en español exporta con punto y coma).
	 */
	private static char detectarDelimitador(BufferedReader lector) throws IOException {
		lector.mark(64 * 1024);
		String encabezado = lector.readLine();
		lector.reset();
		if (encabezado == null) {
			throw new EtlException("El archivo está vacío");
		}
		long comas = encabezado.chars().filter(c -> c == ',').count();
		long puntoYComa = encabezado.chars().filter(c -> c == ';').count();
		return puntoYComa > comas ? ';' : ',';
	}

	/**
	 * Relaciona cada columna esperada con el nombre real del encabezado,
	 * tolerando mayúsculas, espacios y el BOM que agregan algunos editores.
	 */
	private static Map<String, String> mapearColumnas(List<String> encabezados) {
		Map<String, String> reales = new HashMap<>();
		for (String encabezado : encabezados) {
			reales.put(normalizarEncabezado(encabezado), encabezado);
		}

		List<String> faltantes = new ArrayList<>();
		Map<String, String> columnas = new HashMap<>();
		for (String esperada : ColumnasCsv.OBLIGATORIAS) {
			String real = reales.get(esperada);
			if (real == null) {
				faltantes.add(esperada);
			}
			else {
				columnas.put(esperada, real);
			}
		}
		if (!faltantes.isEmpty()) {
			throw new EtlException("Faltan columnas obligatorias: " + String.join(", ", faltantes));
		}
		return columnas;
	}

	private static String normalizarEncabezado(String encabezado) {
		return encabezado.replace("﻿", "").trim().toLowerCase(Locale.ROOT).replace(' ', '_');
	}

	private static Map<String, String> aMapa(CSVRecord fila, Map<String, String> columnas) {
		Map<String, String> mapa = new HashMap<>();
		columnas.forEach((esperada, real) -> mapa.put(esperada, fila.isMapped(real) && fila.isSet(real) ? fila.get(real) : ""));
		return mapa;
	}

	private static TransformadorRegistro.Resultado filaInconsistente(CSVRecord fila, Map<String, String> columnas,
			int columnasEncabezado, long numeroFila) {
		String causa = "la fila tiene " + fila.size() + " columnas y el encabezado " + columnasEncabezado;
		return new TransformadorRegistro.Rechazado(new RegistroRechazado(numeroFila, List.of(causa), aMapa(fila, columnas)));
	}

}
