Proyecto final de Sistemas de bases de datos 2, segundo semestre 2026

| | |
|---|---|
| **Estudiante** | Angel Abelino Son Vicente |
| **Carnet** | 202030885 |


# Pruebas Específicas · MongoDB y Neo4j

Aplicación web para cargar, limpiar, analizar y visualizar el dataset abierto de **Pruebas Específicas** de la Facultad de Ingeniería (2015-2024). Los datos se guardan al mismo tiempo en una base documental (MongoDB), donde se calculan las estadísticas, y en una base de grafos (Neo4j), donde se exploran las relaciones entre aspirantes, carreras, materias e instituciones.


El frontend nunca se conecta a las bases de datos: todo pasa por la API. La arquitectura completa está en el [manual técnico](docs/manual-tecnico.md).

## Requisitos

Docker con Docker Compose, Java 21 y Node.js 20 o superior (con npm). Maven no hace falta instalarlo: el backend trae `mvnw`.

## Levantar el proyecto

### 1. Bases de datos

```bash
docker compose up -d
```

Levanta MongoDB en `localhost:27017` y Neo4j en `localhost:7687` (Neo4j Browser en http://localhost:7474). Los usuarios y contraseñas por defecto están en [.env.example](.env.example); para cambiarlos, copie ese archivo a `.env` antes de levantar los contenedores.

### 2. Backend

```bash
cd backend
./mvnw spring-boot:run
```

La API queda en http://localhost:8080/api. Al arrancar crea los índices de MongoDB y las restricciones de Neo4j. Para comprobar las conexiones: http://localhost:8080/api/health.

### 3. Frontend

```bash
cd frontend
npm ci
npm start
```

La aplicación queda en http://localhost:4200.

### 4. Cargar los datos

Los CSV descargados del portal de datos abiertos están en [data/](data/) (uno por año, 208,453 filas). Se cargan desde la pantalla **Carga de archivos**, seleccionando los diez archivos a la vez, o desde la terminal:

```bash
curl $(for f in data/*.csv; do printf -- '-F archivos=@%s ' "$f"; done) http://localhost:8080/api/cargas
```

La carga completa tarda menos de un minuto. Volver a cargar un archivo no duplica datos.

## Pruebas

```bash
cd backend && ./mvnw test        # requiere las bases de datos levantadas
cd frontend && npx ng test --watch=false
```

## Estructura

```
backend/                 API REST (Spring Boot)
  src/main/java/com/example/backend/
    etl/                 lectura del CSV, reglas de limpieza y catálogos
    carga/               carga por lotes en MongoDB y Neo4j
    consultas/mongo/     ejecuta las agregaciones de resources/queries/mongo
    consultas/neo4j/     ejecuta las consultas de resources/queries/cypher
    busqueda/            buscador con filtros y paginación
    error/               manejo global de errores
    config/              CORS, índices y restricciones
  src/main/resources/
    queries/mongo/       q01.json … q20.json (pipelines de agregación)
    queries/cypher/      c01.cypher … c05.cypher
    catalogos/           nombres completos de carreras y municipios
frontend/                aplicación Angular
  src/app/pages/         carga, dashboard, grafo, buscador
data/                    CSV del dataset 2015-2024
docs/                    documentación
docker-compose.yml
```

