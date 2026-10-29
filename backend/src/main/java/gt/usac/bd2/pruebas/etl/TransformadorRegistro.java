package gt.usac.bd2.pruebas.etl;

import static gt.usac.bd2.pruebas.etl.ColumnasCsv.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Valida y transforma una fila del CSV. Revisa todos los campos antes de
 * decidir, para que un registro rechazado muestre todas sus causas y no
 * solo la primera.
 *
 * <p>Se rechaza la fila cuando falta o es inválido un dato sin el cual la
 * evaluación no tiene sentido: correlativo, materia, fecha, intento, año de
 * ingreso o resultado. Los datos demográficos faltantes (sexo, año de
 * nacimiento, ubicación, tipo de institución) no rechazan la fila: se
 * guardan como SIN REGISTRO o null y se excluyen solo de los análisis que
 * los necesitan.
 */
@Component
public class TransformadorRegistro {

	public sealed interface Resultado permits Valido, Rechazado {
	}

	public record Valido(RegistroEvaluacion registro) implements Resultado {
	}

	public record Rechazado(RegistroRechazado registro) implements Resultado {
	}

	public Resultado transformar(Map<String, String> fila, long numeroFila) {
		List<String> causas = new ArrayList<>();

		String correlativo = ReglasLimpieza.normalizarTexto(fila.get(CORRELATIVO)).toLowerCase(Locale.ROOT);
		if (ReglasLimpieza.esVacio(correlativo.toUpperCase(Locale.ROOT))) {
			causas.add("correlativo_aspirante vacío");
		}

		String materia = ReglasLimpieza.normalizarTexto(fila.get(MATERIA));
		if (ReglasLimpieza.esVacio(materia)) {
			causas.add("materia vacía");
		}

		Optional<LocalDate> fecha = ReglasLimpieza.aFecha(fila.get(FECHA_ASIGNACION));
		if (fecha.isEmpty()) {
			causas.add("fecha_asignacion inválida: '" + valor(fila, FECHA_ASIGNACION) + "'");
		}

		Optional<Integer> intento = ReglasLimpieza.aEntero(fila.get(NUMERO_FECHA_EVALUACION));
		if (intento.isEmpty() || intento.get() < 1) {
			causas.add("numero_de_fecha_de_evaluacion inválido: '" + valor(fila, NUMERO_FECHA_EVALUACION) + "'");
		}

		Optional<Integer> anioIngreso = ReglasLimpieza.aEntero(fila.get(ANIO_INGRESO))
				.filter(a -> a >= 2000 && a <= ReglasLimpieza.ANIO_MAXIMO);
		if (anioIngreso.isEmpty()) {
			causas.add("anio_de_ingreso inválido: '" + valor(fila, ANIO_INGRESO) + "'");
		}

		Optional<Boolean> aprobado = ReglasLimpieza.aAprobado(fila.get(APROBACION));
		if (aprobado.isEmpty()) {
			causas.add("aprobacion con valor desconocido: '" + valor(fila, APROBACION) + "'");
		}

		if (!causas.isEmpty()) {
			return new Rechazado(new RegistroRechazado(numeroFila, causas, fila));
		}

		// SIN REGISTRO o un año imposible se tratan como dato faltante, no como error
		Integer anioNacimiento = ReglasLimpieza.aEntero(fila.get(ANIO_NACIMIENTO))
				.filter(a -> a >= ReglasLimpieza.ANIO_MINIMO && a <= anioIngreso.get())
				.orElse(null);

		LocalDate fechaAsignacion = fecha.get();
		return new Valido(new RegistroEvaluacion(
				numeroFila,
				correlativo,
				fechaAsignacion,
				fechaAsignacion.getYear(),
				fechaAsignacion.getMonthValue(),
				ReglasLimpieza.aSexo(fila.get(SEXO)),
				anioNacimiento,
				ReglasLimpieza.calcularEdad(anioNacimiento, anioIngreso.get()),
				materia,
				intento.get(),
				anioIngreso.get(),
				aprobado.get(),
				ReglasLimpieza.aCarrera(fila.get(CARRERA_OBJETIVO)),
				ReglasLimpieza.textoOVacio(fila.get(DEPARTAMENTO), ReglasLimpieza.SIN_REGISTRO),
				ReglasLimpieza.textoOVacio(fila.get(MUNICIPIO), ReglasLimpieza.SIN_REGISTRO),
				ReglasLimpieza.textoOVacio(fila.get(TIPO_INSTITUCION), ReglasLimpieza.SIN_REGISTRO)));
	}

	private static String valor(Map<String, String> fila, String columna) {
		String valor = fila.get(columna);
		return valor == null ? "" : valor.trim();
	}

}
