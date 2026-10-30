/** Tipos de las respuestas del backend. */

export type EstadoCarga = 'EN_PROCESO' | 'COMPLETADO' | 'COMPLETADO_CON_RECHAZOS' | 'FALLIDO';

/** Resultado de un archivo en POST /api/cargas. */
export interface ResumenCarga {
  id: string | null;
  nombreArchivo: string;
  leidos: number;
  procesados: number;
  rechazados: number;
  duplicados: number;
  tiempoMs: number;
  estado: EstadoCarga;
  mensaje: string | null;
}

/** Documento de la colección cargas (GET /api/cargas). */
export interface CargaHistorial {
  _id: string;
  archivo: string;
  estado: EstadoCarga;
  fecha_inicio: string;
  fecha_fin?: string;
  leidos?: number;
  procesados?: number;
  rechazados?: number;
  duplicados?: number;
  tiempo_ms?: number;
  mensaje?: string | null;
}

export interface RegistroRechazado {
  numeroFila: number;
  archivo: string;
  causas: string[];
  filaOriginal: Record<string, string>;
}

export interface PaginaRechazados {
  total: number;
  pagina: number;
  tamano: number;
  datos: RegistroRechazado[];
}

/** Fila de una agregación de MongoDB: el backend devuelve los documentos tal cual. */
export type Fila = Record<string, unknown>;

/** Respuesta de GET /api/mongo/{id}. */
export interface ResultadoConsulta {
  id: number;
  titulo: string;
  descripcion: string;
  coleccion: string;
  total: number;
  tiempoMs: number;
  datos: Fila[];
}

/** Filtro de una consulta Cypher, leído de los comentarios del archivo .cypher. */
export interface ParametroCypher {
  nombre: string;
  tipo: 'correlativo' | 'entero' | 'texto';
  requerido: boolean;
  valorPorDefecto: string | null;
  descripcion: string;
}

/** Elemento de GET /api/grafo. */
export interface ConsultaGrafoInfo {
  id: number;
  titulo: string;
  descripcion: string;
  parametros: ParametroCypher[];
  cypher: string;
}

export interface NodoGrafo {
  id: string;
  nombre: string;
  categoria: string;
  propiedades: Fila;
}

export interface EnlaceGrafo {
  id: string;
  source: string;
  target: string;
  tipo: string;
  propiedades: Fila;
}

/** Respuesta de GET /api/grafo/{id}. */
export interface ResultadoGrafo {
  id: number;
  titulo: string;
  descripcion: string;
  parametros: Record<string, unknown>;
  tiempoMs: number;
  nodes: NodoGrafo[];
  links: EnlaceGrafo[];
  filas: Fila[];
}

/** Valores distintos para los filtros (GET /api/buscar/opciones). */
export interface OpcionesFiltro {
  carreras: string[];
  materias: string[];
  departamentos: string[];
  municipios: { departamento: string; municipio: string }[];
  tiposInstitucion: string[];
  aniosIngreso: number[];
}
