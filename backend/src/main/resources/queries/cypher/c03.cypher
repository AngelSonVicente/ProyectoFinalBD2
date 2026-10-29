// id: 3
// titulo: Reprobaron en el primer intento y aprobaron después
// descripcion: Aspirantes con una relación REALIZO {intento: 1, aprobado: false} y otra aprobada en un intento posterior de la misma materia. Muestra ambos intentos y la carrera que buscan.
// parametro: materia | texto | | Materia (vacío = todas)
// parametro: carrera | texto | | Carrera objetivo (vacío = todas)
// parametro: limite | entero | 25 | Cantidad de aspirantes-materia
MATCH (a:Aspirante)-[fallo:REALIZO {intento: 1, aprobado: false}]->(m:Materia)
WHERE $materia IS NULL OR m.nombre = $materia
MATCH (a)-[exito:REALIZO {aprobado: true}]->(m)
WHERE exito.intento > 1
MATCH (a)-[aspira:ASPIRA_A]->(c:Carrera)
WHERE $carrera IS NULL OR c.nombre = $carrera
// Si aprobó en varios intentos posteriores se toma el primero
WITH a, m, c, aspira, fallo, exito
ORDER BY exito.intento
WITH a, m, c, aspira, fallo, collect(exito)[0] AS exito
RETURN a, m, c, aspira, fallo, exito,
       a.id AS aspirante, m.nombre AS materia, c.nombre AS carrera, exito.intento AS intento_aprobado
ORDER BY aspirante, materia
LIMIT $limite
