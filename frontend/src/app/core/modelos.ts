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
