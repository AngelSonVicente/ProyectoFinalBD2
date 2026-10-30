package com.example.backend.config;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tiempos de espera cortos: si MongoDB no está disponible, la solicitud falla
 * en unos segundos con un error controlado en lugar de esperar 30 s (el valor
 * por defecto del driver).
 */
@Configuration
public class MongoConfig {

	@Bean
	MongoClientSettingsBuilderCustomizer tiemposDeEspera() {
		return ajustes -> ajustes
				.applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(5, TimeUnit.SECONDS))
				.applyToSocketSettings(socket -> socket.connectTimeout(5, TimeUnit.SECONDS));
	}

}
