// id: 1
// titulo: Red de un aspirante
// descripcion: Recorre desde un aspirante todas sus relaciones: materias que evaluó (una relación por intento), carrera objetivo e institución, municipio y departamento de procedencia.
// parametro: correlativo | correlativo | requerido | Correlativo del aspirante
MATCH p = (a:Aspirante {id: $correlativo})-[:REALIZO|ASPIRA_A|PROCEDE_DE|UBICADA_EN|PERTENECE_A*1..4]->(destino)
RETURN p,
       type(last(relationships(p))) AS relacion,
       labels(destino)[0] AS tipo,
       coalesce(destino.nombre, destino.tipo) AS destino,
       last(relationships(p)).intento AS intento,
       last(relationships(p)).aprobado AS aprobado
ORDER BY length(p), relacion, destino, intento
