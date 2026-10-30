import type { EChartsOption } from 'echarts';
import { Fila } from '../../core/modelos';
import {
  agrupadas,
  alturaCalor,
  anillo,
  barras,
  columnasApiladas,
  lineas,
  mapaCalor,
} from './graficas';

/** Cómo se muestra una consulta en el dashboard. */
export interface ConfigConsulta {
  id: number;
  /** Arma la gráfica; sin ella la consulta se muestra solo como tabla. */
  grafica?: (datos: Fila[]) => EChartsOption;
  /** Alto de la gráfica en píxeles (por defecto 320). */
  altura?: (datos: Fila[]) => number;
  /** Ocupa todo el ancho de la cuadrícula. */
  ancho?: boolean;
  /** Columnas que no se muestran en la tabla. */
  ocultar?: string[];
  /** Cambia la forma de las filas antes de mostrarlas en la tabla. */
  filas?: (datos: Fila[]) => Fila[];
}

export interface Seccion {
  id: string;
  titulo: string;
  consultas: ConfigConsulta[];
}

const carrera = (f: Fila) => String(f['carrera_objetivo']);
/** Q19: { departamento, carreras: [{ carrera_objetivo, aspirantes }] } → una fila por carrera. */
function desglosarCarreras(datos: Fila[]): Fila[] {
  return datos.flatMap((fila) =>
    ((fila['carreras'] as Fila[] | undefined) ?? []).map((c, i) => ({
      departamento: fila['departamento'],
      puesto: i + 1,
      carrera_objetivo: c['carrera_objetivo'],
      aspirantes: c['aspirantes'],
    })),
  );
}

const materia = (f: Fila) => String(f['materia']);

export const SECCIONES: Seccion[] = [
  {
    id: 'aspirantes',
    titulo: 'Aspirantes',
    consultas: [
      { id: 1, grafica: (d) => anillo(d, 'tipo_institucion', 'aspirantes') },
      {
        id: 12,
        grafica: (d) =>
          agrupadas(d, {
            categoria: 'tipo_institucion',
            serie: 'sexo',
            valor: 'aspirantes',
            apiladas: true,
          }),
      },
      {
        id: 17,
        grafica: (d) =>
          barras(d, {
            categoria: (f) => `${f['municipio']} (${f['departamento']})`,
            valor: 'aspirantes',
            nombre: 'Aspirantes',
            horizontal: true,
            etiquetas: true,
          }),
      },
      {
        id: 5,
        grafica: (d) =>
          barras(d, {
            categoria: carrera,
            valor: 'edad_promedio',
            nombre: 'Edad promedio',
            horizontal: true,
            etiquetas: true,
          }),
        altura: (d) => Math.max(320, d.length * 28 + 40),
      },
      { id: 8, ancho: true },
    ],
  },
  {
    id: 'materias',
    titulo: 'Materias',
    consultas: [
      {
        id: 2,
        grafica: (d) =>
          barras(d, {
            categoria: materia,
            valor: 'aprobadas',
            nombre: 'Aprobadas',
            horizontal: true,
            etiquetas: true,
          }),
      },
      {
        id: 4,
        grafica: (d) =>
          barras(d, {
            categoria: materia,
            valor: 'porcentaje_aprobacion',
            nombre: 'Aprobación',
            horizontal: true,
            sufijo: '%',
            etiquetas: true,
          }),
      },
      {
        id: 9,
        grafica: (d) => lineas(d, { x: 'mes', serie: 'materia', valor: 'evaluaciones' }),
        ancho: true,
      },
      {
        id: 14,
        grafica: (d) =>
          barras(d, {
            categoria: materia,
            valor: 'promedio_intentos',
            nombre: 'Promedio de intentos',
            horizontal: true,
            etiquetas: true,
          }),
      },
    ],
  },
  {
    id: 'aprobacion',
    titulo: 'Aprobación',
    consultas: [
      {
        id: 20,
        grafica: (d) =>
          columnasApiladas(d, 'anio_ingreso', [
            { campo: 'aprobadas', nombre: 'Aprobadas' },
            { campo: 'no_aprobadas', nombre: 'No aprobadas' },
          ]),
      },
      {
        id: 18,
        grafica: (d) =>
          barras(d, {
            categoria: (f) => String(f['tipo_institucion']),
            valor: 'porcentaje_aprobacion',
            nombre: 'Aprobación',
            horizontal: true,
            sufijo: '%',
            etiquetas: true,
          }),
      },
      {
        id: 13,
        grafica: (d) =>
          lineas(d, {
            x: 'edad',
            valor: 'porcentaje_aprobacion',
            nombre: 'Aprobación',
            sufijo: '%',
          }),
      },
      {
        id: 16,
        grafica: (d) =>
          barras(d, {
            categoria: carrera,
            valor: 'aspirantes_reprobados',
            nombre: 'Aspirantes reprobados',
            horizontal: true,
            etiquetas: true,
          }),
      },
      {
        id: 3,
        grafica: (d) =>
          mapaCalor(d, {
            x: 'anio_ingreso',
            y: 'carrera_objetivo',
            valor: 'aspirantes_aprobados',
            nombre: 'Aprobados',
          }),
        altura: (d) => alturaCalor(d, 'carrera_objetivo'),
        ancho: true,
      },
      {
        id: 7,
        grafica: (d) =>
          mapaCalor(d, {
            x: 'tipo_institucion',
            y: 'carrera_objetivo',
            valor: 'edad_promedio',
            nombre: 'Edad promedio',
          }),
        altura: (d) => alturaCalor(d, 'carrera_objetivo'),
        ancho: true,
      },
    ],
  },
  {
    id: 'carreras',
    titulo: 'Carreras',
    consultas: [
      {
        id: 10,
        grafica: (d) =>
          barras(d, {
            categoria: carrera,
            valor: 'aspirantes',
            nombre: 'Aspirantes',
            horizontal: true,
            etiquetas: true,
          }),
      },
      // Cada departamento trae un arreglo con sus tres carreras: se muestra una fila por carrera
      { id: 19, filas: desglosarCarreras },
      // Genera la colección resumen_carrera con $out; _id repite la carrera
      { id: 6, ancho: true, ocultar: ['_id'] },
    ],
  },
];
