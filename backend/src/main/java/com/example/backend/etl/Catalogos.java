package com.example.backend.etl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Valores completos de carreras y municipios (resources/catalogos), tomados de
 * los archivos 2015-2021. Los archivos 2022 en adelante traen esos textos
 * cortados y se completan comparando contra estas listas.
 */
public final class Catalogos {

	public static final Set<String> CARRERAS = cargarCarreras();

	/** Municipios por departamento. */
	public static final Map<String, Set<String>> MUNICIPIOS = cargarMunicipios();

	private Catalogos() {
	}

	public static Set<String> municipiosDe(String departamento) {
		return MUNICIPIOS.getOrDefault(departamento, Set.of());
	}

	private static Set<String> cargarCarreras() {
		Set<String> carreras = new TreeSet<>();
		for (String linea : leer("catalogos/carreras.txt")) {
			if (!linea.isBlank() && !linea.startsWith("#")) {
				carreras.add(ReglasLimpieza.normalizarTexto(linea));
			}
		}
		return Set.copyOf(carreras);
	}

	private static Map<String, Set<String>> cargarMunicipios() {
		Map<String, Set<String>> municipios = new HashMap<>();
		List<String> lineas = leer("catalogos/municipios.csv");
		// La primera línea es el encabezado
		for (String linea : lineas.subList(1, lineas.size())) {
			int coma = linea.indexOf(',');
			if (coma > 0) {
				municipios.computeIfAbsent(ReglasLimpieza.normalizarTexto(linea.substring(0, coma)), d -> new TreeSet<>())
						.add(ReglasLimpieza.normalizarTexto(linea.substring(coma + 1)));
			}
		}
		Map<String, Set<String>> inmutable = new HashMap<>();
		municipios.forEach((departamento, nombres) -> inmutable.put(departamento, Set.copyOf(nombres)));
		return Map.copyOf(inmutable);
	}

	private static List<String> leer(String recurso) {
		InputStream entrada = Catalogos.class.getClassLoader().getResourceAsStream(recurso);
		if (entrada == null) {
			throw new IllegalStateException("No se encontró el recurso " + recurso);
		}
		try (BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
			return lector.lines().toList();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
