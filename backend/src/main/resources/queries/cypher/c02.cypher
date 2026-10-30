// id: 2
// titulo: Carreras más buscadas por departamento
// descripcion: Recorre Departamento ← Municipio ← Institución ← Aspirante → Carrera y cuenta los aspirantes distintos que buscan cada carrera desde cada departamento.
// parametro: departamento | texto | | Departamento de la institución (vacío = todos)
// parametro: limite | entero | 15 | Cantidad de pares departamento-carrera
MATCH (d:Departamento)<-[:PERTENECE_A]-(:Municipio)<-[:UBICADA_EN]-(:Institucion)<-[:PROCEDE_DE]-(a:Aspirante)-[:ASPIRA_A]->(c:Carrera)
WHERE ($departamento IS NULL OR d.nombre = $departamento)
  AND c.nombre <> 'SIN CARRERA'
WITH d, c, count(DISTINCT a) AS aspirantes
ORDER BY aspirantes DESC, d.nombre, c.nombre
LIMIT $limite
RETURN d AS origen, c AS destino, 'ASPIRANTES' AS relacion,
       d.nombre AS departamento, c.nombre AS carrera, aspirantes
