// id: 4
// titulo: Tasa de aprobación por departamento y materia
// descripcion: Agrupa las relaciones REALIZO por departamento de procedencia y materia, y calcula el porcentaje aprobado con la propiedad aprobado. Se puede filtrar por tipo de institución.
// parametro: tipo | texto | | Tipo de institución (vacío = todos)
// parametro: departamento | texto | | Departamento (vacío = todos)
// parametro: materia | texto | | Materia (vacío = todas)
// parametro: limite | entero | 50 | Cantidad de pares departamento-materia
MATCH (d:Departamento)<-[:PERTENECE_A]-(:Municipio)<-[:UBICADA_EN]-(i:Institucion)<-[:PROCEDE_DE]-(:Aspirante)-[r:REALIZO]->(m:Materia)
WHERE ($tipo IS NULL OR i.tipo = $tipo)
  AND ($departamento IS NULL OR d.nombre = $departamento)
  AND ($materia IS NULL OR m.nombre = $materia)
WITH d, m, count(r) AS evaluaciones, sum(CASE WHEN r.aprobado THEN 1 ELSE 0 END) AS aprobadas
RETURN d AS origen, m AS destino, 'TASA_APROBACION' AS relacion,
       d.nombre AS departamento, m.nombre AS materia, evaluaciones, aprobadas,
       round(100.0 * aprobadas / evaluaciones, 2) AS porcentaje_aprobacion
ORDER BY departamento, materia
LIMIT $limite
