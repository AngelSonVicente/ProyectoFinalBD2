package gt.usac.bd2.pruebas.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class LectorCsvTest {

	private static final String ENCABEZADO = "fecha_asignacion,sexo,anio_nacimiento,materia,numero_de_fecha_de_evaluacion,"
			+ "anio_de_ingreso,aprobacion,carrera_objetivo,departamento_institucion_educativa,"
			+ "municipio_institucion_educativa,tipo_institucion_educativa,correlativo_aspirante\n";

	private final LectorCsv lector = new LectorCsv(new TransformadorRegistro());

	private final List<RegistroEvaluacion> validos = new ArrayList<>();

	private final List<RegistroRechazado> rechazados = new ArrayList<>();

	private ResultadoEtl procesar(String contenido) {
		return lector.procesar(new ByteArrayInputStream(contenido.getBytes(StandardCharsets.UTF_8)), "prueba.csv",
				validos::add, rechazados::add);
	}

	@Test
	void transformaUnaFilaValidaDelEnunciado() {
		ResultadoEtl resultado = procesar(ENCABEZADO
				+ "2014-08-23,M,SIN REGISTRO,MATEMATICA,1,2015,NO APROBADO,NINGUNO,GUATEMALA,"
				+ "GUATEMALA - CIUDAD CAPITAL ZONA 1,PRIVADO,949d241ac10256edf11339a303c72a50\n");

		assertThat(resultado.leidos()).isEqualTo(1);
		assertThat(resultado.validos()).isEqualTo(1);
		assertThat(resultado.rechazados()).isZero();

		RegistroEvaluacion r = validos.getFirst();
		assertThat(r.numeroFila()).isEqualTo(2);
		assertThat(r.fechaAsignacion()).isEqualTo(LocalDate.of(2014, 8, 23));
		assertThat(r.mesAsignacion()).isEqualTo(8);
		assertThat(r.anioNacimiento()).isNull();
		assertThat(r.edad()).isNull();
		assertThat(r.aprobado()).isFalse();
		assertThat(r.carreraObjetivo()).isEqualTo(ReglasLimpieza.SIN_CARRERA);
		assertThat(r.municipio()).isEqualTo("GUATEMALA - CIUDAD CAPITAL ZONA 1");
		assertThat(r.correlativoAspirante()).isEqualTo("949d241ac10256edf11339a303c72a50");
	}

	@Test
	void calculaEdadYEstandarizaTexto() {
		procesar(ENCABEZADO + "2014-08-23,f,1998,Matemática,2,2015,aprobado,Ingeniería Civil,"
				+ "guatemala,mixco,público,ABC123\n");

		RegistroEvaluacion r = validos.getFirst();
		assertThat(r.edad()).isEqualTo(17);
		assertThat(r.sexo()).isEqualTo("F");
		assertThat(r.materia()).isEqualTo("MATEMATICA");
		assertThat(r.carreraObjetivo()).isEqualTo("INGENIERIA CIVIL");
		assertThat(r.tipoInstitucion()).isEqualTo("PUBLICO");
		assertThat(r.correlativoAspirante()).isEqualTo("abc123");
		assertThat(r.aprobado()).isTrue();
	}

	@Test
	void rechazaFilasInvalidasConTodasSusCausas() {
		ResultadoEtl resultado = procesar(ENCABEZADO
				+ "fecha-mala,M,2000,,X,2015,PENDIENTE,NINGUNO,GUATEMALA,MIXCO,PRIVADO,\n"
				+ "2014-08-23,M,2000,FISICA,1,2015,APROBADO,NINGUNO,GUATEMALA\n");

		assertThat(resultado.leidos()).isEqualTo(2);
		assertThat(resultado.rechazados()).isEqualTo(2);

		RegistroRechazado primero = rechazados.get(0);
		assertThat(primero.numeroFila()).isEqualTo(2);
		assertThat(primero.causas()).hasSize(5);
		assertThat(primero.causa()).contains("correlativo_aspirante", "materia", "fecha_asignacion",
				"numero_de_fecha_de_evaluacion", "aprobacion");

		assertThat(rechazados.get(1).causa()).contains("9 columnas");
	}

	@Test
	void aceptaPuntoYComaYEncabezadosConBom() {
		String contenido = "﻿" + ENCABEZADO.replace(',', ';')
				+ "2014-08-23;M;1999;FISICA;1;2015;APROBADO;NINGUNO;GUATEMALA;MIXCO;PRIVADO;abc\n";

		ResultadoEtl resultado = procesar(contenido);

		assertThat(resultado.validos()).isEqualTo(1);
	}

	@Test
	void fallaSiFaltanColumnasObligatorias() {
		assertThatThrownBy(() -> procesar("sexo,materia\nM,FISICA\n"))
				.isInstanceOf(EtlException.class)
				.hasMessageContaining("fecha_asignacion")
				.hasMessageContaining("correlativo_aspirante");
	}

	@Test
	void fallaSiElArchivoEstaVacio() {
		assertThatThrownBy(() -> procesar("")).isInstanceOf(EtlException.class).hasMessageContaining("vacío");
	}

}
