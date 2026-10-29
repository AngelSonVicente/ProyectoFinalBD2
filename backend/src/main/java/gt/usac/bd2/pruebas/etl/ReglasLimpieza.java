package gt.usac.bd2.pruebas.etl;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Reglas de estandarización aplicadas a los valores del CSV.
 * Todas son funciones puras para poder probarlas de forma aislada.
 */
public final class ReglasLimpieza {

	/** Valores que el dataset usa para indicar que no hay dato. */
	public static final Set<String> VALORES_VACIOS = Set.of("", "SIN REGISTRO", "NULL", "N/A", "NA", "-");

	public static final String SIN_REGISTRO = "SIN REGISTRO";
	public static final String SIN_CARRERA = "SIN CARRERA";

	public static final int EDAD_MINIMA = 14;
	public static final int EDAD_MAXIMA = 70;
	public static final int ANIO_MINIMO = 1900;
	public static final int ANIO_MAXIMO = 2100;

	private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
			DateTimeFormatter.ISO_LOCAL_DATE,
			DateTimeFormatter.ofPattern("d/M/yyyy"),
			DateTimeFormatter.ofPattern("d-M-yyyy"),
			DateTimeFormatter.ofPattern("yyyy/M/d"));

	private ReglasLimpieza() {
	}

	/**
	 * Quita espacios sobrantes, pasa a mayúsculas y elimina tildes, para que
	 * "Público", "PUBLICO " y "público" terminen siendo el mismo valor.
	 * La Ñ se conserva.
	 */
	public static String normalizarTexto(String valor) {
		if (valor == null) {
			return "";
		}
		String texto = valor.replace(' ', ' ').trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
		texto = texto.replace("Ñ", "\u0000");
		texto = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return texto.replace("\u0000", "Ñ");
	}

	public static boolean esVacio(String valorNormalizado) {
		return VALORES_VACIOS.contains(valorNormalizado);
	}

	/** Texto opcional: si viene vacío o "SIN REGISTRO" se usa el valor indicado. */
	public static String textoOVacio(String valor, String siVacio) {
		String texto = normalizarTexto(valor);
		return esVacio(texto) ? siVacio : texto;
	}

	/**
	 * Convierte a entero aceptando también valores como "2015.0" que suelen
	 * aparecer cuando el archivo pasó por una hoja de cálculo.
	 */
	public static Optional<Integer> aEntero(String valor) {
		String texto = normalizarTexto(valor);
		if (esVacio(texto)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Integer.parseInt(texto));
		}
		catch (NumberFormatException e) {
			try {
				double numero = Double.parseDouble(texto);
				if (numero == Math.rint(numero) && !Double.isInfinite(numero)) {
					return Optional.of((int) numero);
				}
			}
			catch (NumberFormatException ignorada) {
				// no es número
			}
			return Optional.empty();
		}
	}

	public static Optional<LocalDate> aFecha(String valor) {
		String texto = valor == null ? "" : valor.trim();
		// Algunas fechas pueden traer hora: "2014-08-23 00:00:00"
		int espacio = texto.indexOf(' ');
		if (espacio > 0) {
			texto = texto.substring(0, espacio);
		}
		int t = texto.indexOf('T');
		if (t > 0) {
			texto = texto.substring(0, t);
		}
		for (DateTimeFormatter formato : FORMATOS_FECHA) {
			try {
				return Optional.of(LocalDate.parse(texto, formato));
			}
			catch (DateTimeParseException e) {
				// se prueba el siguiente formato
			}
		}
		return Optional.empty();
	}

	/** APROBADO → true, NO APROBADO / REPROBADO → false, cualquier otro valor → vacío. */
	public static Optional<Boolean> aAprobado(String valor) {
		return switch (normalizarTexto(valor)) {
			case "APROBADO", "APROBO", "SI" -> Optional.of(true);
			case "NO APROBADO", "REPROBADO", "NO APROBO", "NO" -> Optional.of(false);
			default -> Optional.empty();
		};
	}

	/** Estandariza el sexo a M o F; cualquier otro valor queda como SIN REGISTRO. */
	public static String aSexo(String valor) {
		return switch (normalizarTexto(valor)) {
			case "M", "MASCULINO", "HOMBRE" -> "M";
			case "F", "FEMENINO", "MUJER" -> "F";
			default -> SIN_REGISTRO;
		};
	}

	/** NINGUNO o vacío se guarda como SIN CARRERA para poder agruparlo. */
	public static String aCarrera(String valor) {
		String texto = normalizarTexto(valor);
		return esVacio(texto) || texto.equals("NINGUNO") || texto.equals("NINGUNA") ? SIN_CARRERA : texto;
	}

	/**
	 * Edad aproximada al momento del ingreso. Se descarta (null) si no hay
	 * año de nacimiento o si el resultado no es razonable para un aspirante.
	 */
	public static Integer calcularEdad(Integer anioNacimiento, int anioIngreso) {
		if (anioNacimiento == null) {
			return null;
		}
		int edad = anioIngreso - anioNacimiento;
		return edad >= EDAD_MINIMA && edad <= EDAD_MAXIMA ? edad : null;
	}

}
