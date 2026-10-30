package com.example.backend.etl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ReglasLimpiezaTest {

	@Test
	void normalizaEspaciosMayusculasYTildesConservandoLaEnie() {
		assertThat(ReglasLimpieza.normalizarTexto("  Público   ")).isEqualTo("PUBLICO");
		assertThat(ReglasLimpieza.normalizarTexto("Ingeniería  en  Ciencias")).isEqualTo("INGENIERIA EN CIENCIAS");
		assertThat(ReglasLimpieza.normalizarTexto("Peñate")).isEqualTo("PEÑATE");
		assertThat(ReglasLimpieza.normalizarTexto(null)).isEmpty();
	}

	@Test
	void convierteEnterosIncluyendoDecimalesExactos() {
		assertThat(ReglasLimpieza.aEntero("2015")).contains(2015);
		assertThat(ReglasLimpieza.aEntero("2015.0")).contains(2015);
		assertThat(ReglasLimpieza.aEntero("SIN REGISTRO")).isEmpty();
		assertThat(ReglasLimpieza.aEntero("12.5")).isEmpty();
		assertThat(ReglasLimpieza.aEntero("abc")).isEmpty();
	}

	@Test
	void aceptaVariosFormatosDeFecha() {
		LocalDate esperada = LocalDate.of(2014, 8, 23);
		assertThat(ReglasLimpieza.aFecha("2014-08-23")).contains(esperada);
		assertThat(ReglasLimpieza.aFecha("23/08/2014")).contains(esperada);
		assertThat(ReglasLimpieza.aFecha("2014-08-23 00:00:00")).contains(esperada);
		assertThat(ReglasLimpieza.aFecha("2014-13-40")).isEmpty();
		assertThat(ReglasLimpieza.aFecha("")).isEmpty();
	}

	@Test
	void interpretaElResultadoDeLaEvaluacion() {
		assertThat(ReglasLimpieza.aAprobado("APROBADO")).contains(true);
		assertThat(ReglasLimpieza.aAprobado(" no aprobado ")).contains(false);
		assertThat(ReglasLimpieza.aAprobado("PENDIENTE")).isEmpty();
	}

	@Test
	void estandarizaSexoYCarrera() {
		assertThat(ReglasLimpieza.aSexo("m")).isEqualTo("M");
		assertThat(ReglasLimpieza.aSexo("Femenino")).isEqualTo("F");
		assertThat(ReglasLimpieza.aSexo("")).isEqualTo(ReglasLimpieza.SIN_REGISTRO);
		assertThat(ReglasLimpieza.aCarrera("NINGUNO")).isEqualTo(ReglasLimpieza.SIN_CARRERA);
		assertThat(ReglasLimpieza.aCarrera("ingeniería civil")).isEqualTo("INGENIERIA CIVIL");
	}

	@Test
	void calculaEdadSoloDentroDelRangoValido() {
		assertThat(ReglasLimpieza.calcularEdad(1998, 2015)).isEqualTo(17);
		assertThat(ReglasLimpieza.calcularEdad(null, 2015)).isNull();
		assertThat(ReglasLimpieza.calcularEdad(2010, 2015)).isNull();
		assertThat(ReglasLimpieza.calcularEdad(1900, 2015)).isNull();
	}

	@Test
	void completaTextosCortadosConElCatalogo() {
		List<String> catalogo = List.of("INGENIERIA MECANICA", "INGENIERIA MECANICA ELECTRICA",
				"INGENIERIA MECANICA INDUSTRIAL", "INGENIERIA EN CIENCIAS Y SISTEMAS");

		// Un solo candidato
		assertThat(ReglasLimpieza.completarTruncado("INGENIERIA EN CIENCIAS Y ", catalogo))
				.isEqualTo("INGENIERIA EN CIENCIAS Y SISTEMAS");
		// Valor completo: se deja igual aunque sea inicio de otros
		assertThat(ReglasLimpieza.completarTruncado("INGENIERIA MECANICA", catalogo)).isEqualTo("INGENIERIA MECANICA");
		// El espacio final indica que faltaba una palabra: no es MECANICA a secas
		assertThat(ReglasLimpieza.completarTruncado("INGENIERIA MECANICA ", catalogo))
				.isEqualTo("INGENIERIA MECANICA (ELECTRICA O INDUSTRIAL)");
		// Valor que no está en el catálogo
		assertThat(ReglasLimpieza.completarTruncado("MOMOSTENANGO", catalogo)).isEqualTo("MOMOSTENANGO");
	}

	@Test
	void completaCarrerasYMunicipiosDeLosArchivosRecientes() {
		assertThat(ReglasLimpieza.aCarrera("INGENIERIA EN CIENCIAS Y ")).isEqualTo("INGENIERIA EN CIENCIAS Y SISTEMAS");
		assertThat(ReglasLimpieza.aMunicipio("ANTIGUA GUATEMAL", "SACATEPEQUEZ")).isEqualTo("ANTIGUA GUATEMALA");
		// Hay más de 20 zonas que empiezan igual
		assertThat(ReglasLimpieza.aMunicipio("GUATEMALA - CIUDAD ", "GUATEMALA"))
				.isEqualTo("GUATEMALA - CIUDAD CAPITAL ZONA (SIN ESPECIFICAR)");
		assertThat(ReglasLimpieza.aMunicipio("SIN DATOS", "SIN DATOS")).isEqualTo(ReglasLimpieza.SIN_REGISTRO);
	}

	@Test
	void recortaElCorrelativoAVeinteCaracteres() {
		String completo = "949D241AC10256EDF11339A303C72A50";
		assertThat(ReglasLimpieza.aCorrelativo(completo)).isEqualTo("949d241ac10256edf113");
		assertThat(ReglasLimpieza.aCorrelativo("949d241ac10256edf1133")).isEqualTo("949d241ac10256edf113");
		assertThat(ReglasLimpieza.aCorrelativo("asp001")).isEqualTo("asp001");
	}

}
