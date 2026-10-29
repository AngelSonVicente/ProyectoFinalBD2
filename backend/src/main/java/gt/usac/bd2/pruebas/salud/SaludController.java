package gt.usac.bd2.pruebas.salud;

import java.time.Instant;

import org.bson.Document;
import org.neo4j.driver.Driver;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Indica si la API puede comunicarse con cada base de datos. Responde 200
 * cuando las dos están disponibles y 503 si alguna falla, siempre con el
 * detalle de cada una.
 */
@RestController
@RequestMapping("/api/health")
public class SaludController {

	public enum Estado {
		DISPONIBLE, NO_DISPONIBLE
	}

	public record Componente(Estado estado, long tiempoMs, String detalle) {
	}

	public record Salud(Estado estado, Componente mongodb, Componente neo4j, Instant timestamp) {
	}

	private final MongoTemplate mongoTemplate;

	private final Driver neo4jDriver;

	public SaludController(MongoTemplate mongoTemplate, Driver neo4jDriver) {
		this.mongoTemplate = mongoTemplate;
		this.neo4jDriver = neo4jDriver;
	}

	@GetMapping
	public ResponseEntity<Salud> salud() {
		Componente mongodb = verificar(() -> mongoTemplate.getDb().runCommand(new Document("ping", 1)));
		Componente neo4j = verificar(neo4jDriver::verifyConnectivity);

		boolean todoBien = mongodb.estado() == Estado.DISPONIBLE && neo4j.estado() == Estado.DISPONIBLE;
		Salud salud = new Salud(todoBien ? Estado.DISPONIBLE : Estado.NO_DISPONIBLE, mongodb, neo4j, Instant.now());
		return ResponseEntity.status(todoBien ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(salud);
	}

	private static Componente verificar(Runnable prueba) {
		long inicio = System.nanoTime();
		try {
			prueba.run();
			return new Componente(Estado.DISPONIBLE, (System.nanoTime() - inicio) / 1_000_000, null);
		}
		catch (RuntimeException e) {
			return new Componente(Estado.NO_DISPONIBLE, (System.nanoTime() - inicio) / 1_000_000, e.getMessage());
		}
	}

}
