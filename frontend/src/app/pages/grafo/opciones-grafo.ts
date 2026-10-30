import type { EChartsOption } from 'echarts';
import { EnlaceGrafo, Fila, NodoGrafo, ResultadoGrafo } from '../../core/modelos';
import { COLORES } from '../dashboard/graficas';
import { formatearCelda } from '../dashboard/tabla-datos/tabla-datos';

/**
 * Orden fijo de las etiquetas del grafo: cada tipo de nodo conserva su color
 * sin importar qué consulta se ejecute.
 */
export const CATEGORIAS = [
  'Aspirante',
  'Carrera',
  'Materia',
  'Institucion',
  'Municipio',
  'Departamento',
];

export const NOMBRES_CATEGORIA: Record<string, string> = {
  Aspirante: 'Aspirante',
  Carrera: 'Carrera',
  Materia: 'Materia',
  Institucion: 'Institución',
  Municipio: 'Municipio',
  Departamento: 'Departamento',
};

/** Relaciones calculadas por la consulta (no existen en Neo4j) y la propiedad que define su grosor. */
const PESO_RELACION: Record<string, string> = {
  ASPIRANTES: 'aspirantes',
  TASA_APROBACION: 'porcentaje_aprobacion',
};

/** Departamento usa el violeta: el verde quedaría junto al aqua de Materia y se confunden. */
const COLOR_CATEGORIA: Record<string, string> = {
  Aspirante: COLORES[0],
  Carrera: COLORES[1],
  Materia: COLORES[2],
  Institucion: COLORES[3],
  Municipio: COLORES[4],
  Departamento: COLORES[6],
};

const TEXTO = '#52514e';
const ARISTA = '#a3a29c';

/** Lista "clave: valor" para los tooltips. */
function propiedades(p: Fila): string {
  return Object.entries(p)
    .filter(([clave]) => clave !== 'id' && clave !== 'clave')
    .map(([clave, valor]) => `${clave.replace(/_/g, ' ')}: <b>${formatearCelda(valor, clave)}</b>`)
    .join('<br>');
}

function categoria(nodo: NodoGrafo): number {
  const indice = CATEGORIAS.indexOf(nodo.categoria);
  return indice >= 0 ? indice : CATEGORIAS.length;
}

/** Grosor entre 1 y 8 según la propiedad numérica de una relación calculada. */
function grosores(enlaces: EnlaceGrafo[]): Map<string, number> {
  const pesos = new Map<string, number>();
  enlaces.forEach((e) => {
    const campo = PESO_RELACION[e.tipo];
    const valor = campo ? Number(e.propiedades[campo]) : NaN;
    if (!Number.isNaN(valor)) {
      pesos.set(e.id, valor);
    }
  });
  if (pesos.size === 0) {
    return pesos;
  }
  const valores = [...pesos.values()];
  const min = Math.min(...valores);
  const rango = Math.max(...valores) - min || 1;
  pesos.forEach((valor, id) => pesos.set(id, 1 + ((valor - min) / rango) * 7));
  return pesos;
}

function fuerza(total: number) {
  if (total <= 20) {
    return { repulsion: 700, edgeLength: [120, 200], gravity: 0.05 };
  }
  if (total <= 60) {
    return { repulsion: 220, edgeLength: [50, 120], gravity: 0.08 };
  }
  return { repulsion: 110, edgeLength: [25, 70], gravity: 0.15 };
}

/**
 * Convierte la respuesta {nodes, links} del backend en una serie "graph" de
 * ECharts con layout de fuerzas. El tamaño del nodo crece con su cantidad de
 * conexiones; los intentos no aprobados se dibujan punteados.
 */
export function opcionesGrafo(resultado: ResultadoGrafo): EChartsOption {
  const grados = new Map<string, number>();
  resultado.links.forEach((e) => {
    grados.set(e.source, (grados.get(e.source) ?? 0) + 1);
    grados.set(e.target, (grados.get(e.target) ?? 0) + 1);
  });
  const total = resultado.nodes.length;
  const grosor = grosores(resultado.links);
  const conteo = new Map<string, number>();
  resultado.nodes.forEach((n) => {
    const nombre = NOMBRES_CATEGORIA[n.categoria] ?? n.categoria;
    conteo.set(nombre, (conteo.get(nombre) ?? 0) + 1);
  });

  return {
    color: COLORES,
    textStyle: { fontFamily: 'Roboto, sans-serif', color: TEXTO },
    tooltip: {
      confine: true,
      formatter: (p) => {
        const dato = (p as unknown as { data: { nodo?: NodoGrafo; enlace?: EnlaceGrafo } }).data;
        if (dato.nodo) {
          const n = dato.nodo;
          return `<b>${n.nombre}</b> · ${NOMBRES_CATEGORIA[n.categoria] ?? n.categoria}<br>${propiedades(n.propiedades)}`;
        }
        const e = dato.enlace!;
        const detalle = propiedades(e.propiedades);
        return `<b>${e.tipo}</b>${detalle ? '<br>' + detalle : ''}`;
      },
    },
    // La leyenda también sirve para ocultar o mostrar un tipo de nodo
    legend: {
      top: 8,
      data: CATEGORIAS.map((c) => NOMBRES_CATEGORIA[c]).filter((c) => conteo.has(c)),
      formatter: (nombre) => `${nombre} (${conteo.get(nombre) ?? 0})`,
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        categories: CATEGORIAS.map((c) => ({
          name: NOMBRES_CATEGORIA[c],
          itemStyle: { color: COLOR_CATEGORIA[c] },
        })),
        // El layout de fuerzas no se ajusta solo al área: se separa más cuando hay pocos nodos
        // y se compacta cuando hay muchos para que el grafo quepa completo
        force: fuerza(total),
        zoom: total > 60 ? 0.9 : 1,
        emphasis: { focus: 'adjacency', lineStyle: { width: 3 } },
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 6,
        // Separa las aristas paralelas (varios intentos de la misma materia)
        autoCurveness: true,
        label: { position: 'right', color: TEXTO, fontSize: 11 },
        labelLayout: { hideOverlap: true },
        data: resultado.nodes.map((n) => {
          const grado = grados.get(n.id) ?? 0;
          return {
            id: n.id,
            name: n.nombre,
            category: categoria(n),
            symbolSize: Math.min(
              48,
              (n.categoria === 'Aspirante' ? 10 : 18) + Math.sqrt(grado) * 4,
            ),
            // Los aspirantes solo llevan nombre cuando hay pocos nodos
            label: { show: total <= 20 || n.categoria !== 'Aspirante' },
            itemStyle: { borderColor: '#ffffff', borderWidth: 1.5 },
            nodo: n,
          };
        }),
        links: resultado.links.map((e) => ({
          source: e.source,
          target: e.target,
          lineStyle: {
            color: ARISTA,
            width: grosor.get(e.id) ?? 1.2,
            opacity: 0.8,
            type: e.propiedades['aprobado'] === false ? ('dashed' as const) : ('solid' as const),
          },
          enlace: e,
        })),
      },
    ],
  };
}
