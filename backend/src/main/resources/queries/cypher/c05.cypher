// id: 5
// titulo: Aspirantes del mismo municipio con la misma carrera
// descripcion: Encuentra grupos de aspirantes que proceden del mismo municipio y buscan la misma carrera; quedan conectados entre sí a través de esos nodos compartidos.
// parametro: departamento | texto | | Departamento (vacío = todos)
// parametro: carrera | texto | | Carrera objetivo (vacío = todas)
// parametro: limite | entero | 5 | Cantidad de grupos municipio-carrera (cada uno muestra hasta 15 aspirantes)
MATCH (d:Departamento)<-[:PERTENECE_A]-(mu:Municipio)<-[:UBICADA_EN]-(:Institucion)<-[:PROCEDE_DE]-(a:Aspirante)-[:ASPIRA_A]->(c:Carrera)
WHERE ($departamento IS NULL OR d.nombre = $departamento)
  AND ($carrera IS NULL OR c.nombre = $carrera)
  AND c.nombre <> 'SIN CARRERA'
WITH mu, c, collect(DISTINCT a) AS aspirantes
WHERE size(aspirantes) > 1
ORDER BY size(aspirantes) DESC, mu.clave, c.nombre
LIMIT $limite
UNWIND aspirantes[0..15] AS a
MATCH procedencia = (a)-[:PROCEDE_DE]->(:Institucion)-[:UBICADA_EN]->(mu)
MATCH aspira = (a)-[:ASPIRA_A]->(c)
RETURN procedencia, aspira,
       mu.nombre AS municipio, c.nombre AS carrera, size(aspirantes) AS aspirantes_en_grupo, a.id AS aspirante
