import type { EChartsOption } from 'echarts';
import { Fila } from '../../core/modelos';

/** Colores por serie, siempre en el mismo orden para que cada categoría conserve su color. */
export const COLORES = [
  '#2a78d6',
  '#eb6834',
  '#1baf7a',
  '#eda100',
  '#e87ba4',
  '#008300',
  '#6250d6',
  '#e34948',
];

/** Escala de un solo tono para los mapas de calor (de menor a mayor). */
const ESCALA = ['#e3eefb', '#9cc3f0', '#4f8fdc', '#1f5fae', '#0f3a73'];

const TEXTO = '#52514e';
const LINEAS = '#e4e3df';
const SUPERFICIE = '#ffffff';

const numero = new Intl.NumberFormat('es-GT', { maximumFractionDigits: 2 });

export function formatearNumero(valor: unknown, sufijo = ''): string {
  return typeof valor === 'number' ? numero.format(valor) + sufijo : String(valor ?? '—');
}

/** Acorta los nombres largos de carreras para los ejes; el tooltip muestra el nombre completo. */
export function abreviar(texto: string, maximo = 32): string {
  const corto = texto.replace(/^INGENIER[IÍ]A (EN )?/, 'ING. ');
  return corto.length > maximo ? corto.slice(0, maximo - 1) + '…' : corto;
}

const texto = (fila: Fila, campo: string) => String(fila[campo] ?? 'SIN REGISTRO');
const valorDe = (fila: Fila, campo: string) => Number(fila[campo] ?? 0);

function base(): EChartsOption {
  return {
    color: COLORES,
    textStyle: { fontFamily: 'Roboto, sans-serif', color: TEXTO },
    grid: { left: 8, right: 24, top: 36, bottom: 8 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      confine: true,
    },
  };
}

const ejeValor = (sufijo = '') => ({
  type: 'value' as const,
  splitLine: { lineStyle: { color: LINEAS } },
  axisLabel: { color: TEXTO, formatter: (v: number) => formatearNumero(v, sufijo) },
});

const ejeCategoria = (categorias: string[], extra: Record<string, unknown> = {}) => ({
  type: 'category' as const,
  data: categorias,
  axisTick: { show: false },
  axisLine: { lineStyle: { color: LINEAS } },
  axisLabel: { color: TEXTO, formatter: (v: string) => abreviar(v) },
  ...extra,
});

interface OpcionesBarras {
  categoria: (fila: Fila) => string;
  valor: string;
  nombre: string;
  horizontal?: boolean;
  sufijo?: string;
  /** Muestra el valor al final de cada barra (solo para pocas barras). */
  etiquetas?: boolean;
}

/** Una sola serie de barras. Las horizontales conservan el orden en que vienen los datos. */
export function barras(datos: Fila[], o: OpcionesBarras): EChartsOption {
  const categorias = datos.map(o.categoria);
  const eje = ejeCategoria(categorias, o.horizontal ? { inverse: true } : {});
  return {
    ...base(),
    grid: { left: 8, right: o.etiquetas ? 56 : 24, top: 16, bottom: 8 },
    xAxis: o.horizontal ? ejeValor(o.sufijo) : eje,
    yAxis: o.horizontal ? eje : ejeValor(o.sufijo),
    tooltip: { ...base().tooltip, valueFormatter: (v) => formatearNumero(v, o.sufijo) },
    series: [
      {
        type: 'bar',
        name: o.nombre,
        data: datos.map((f) => valorDe(f, o.valor)),
        barMaxWidth: 28,
        itemStyle: { borderRadius: o.horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0] },
        label: {
          show: !!o.etiquetas,
          position: o.horizontal ? 'right' : 'top',
          color: TEXTO,
          formatter: (p) => formatearNumero(p.value, o.sufijo),
        },
      },
    ],
  };
}

interface OpcionesAgrupadas {
  categoria: string;
  serie: string;
  valor: string;
  apiladas?: boolean;
  sufijo?: string;
  /** Nombres que se muestran en la leyenda en lugar del valor del campo. */
  etiquetas?: Record<string, string>;
}

/** Barras agrupadas (o apiladas): una serie por cada valor distinto del campo "serie". */
export function agrupadas(datos: Fila[], o: OpcionesAgrupadas): EChartsOption {
  const { categorias, series, valores } = pivotar(datos, o.categoria, o.serie, o.valor);
  return {
    ...base(),
    legend: { top: 0, type: 'scroll' },
    xAxis: ejeCategoria(categorias),
    yAxis: ejeValor(o.sufijo),
    tooltip: { ...base().tooltip, valueFormatter: (v) => formatearNumero(v, o.sufijo) },
    series: series.map((serie) => ({
      type: 'bar' as const,
      name: o.etiquetas?.[serie] ?? serie,
      stack: o.apiladas ? 'total' : undefined,
      data: categorias.map((c) => valores.get(`${c}|${serie}`) ?? 0),
      barMaxWidth: 40,
      // Separación blanca entre segmentos apilados
      itemStyle: o.apiladas
        ? { borderColor: SUPERFICIE, borderWidth: 1 }
        : { borderRadius: [4, 4, 0, 0] },
    })),
  };
}

/** Varias columnas numéricas de cada fila como series apiladas (por ejemplo, aprobadas y no aprobadas). */
export function columnasApiladas(
  datos: Fila[],
  categoria: string,
  columnas: { campo: string; nombre: string }[],
): EChartsOption {
  return {
    ...base(),
    legend: { top: 0 },
    xAxis: ejeCategoria(datos.map((f) => texto(f, categoria))),
    yAxis: ejeValor(),
    tooltip: { ...base().tooltip, valueFormatter: (v) => formatearNumero(v) },
    series: columnas.map((c) => ({
      type: 'bar' as const,
      name: c.nombre,
      stack: 'total',
      data: datos.map((f) => valorDe(f, c.campo)),
      barMaxWidth: 48,
      itemStyle: { borderColor: SUPERFICIE, borderWidth: 1 },
    })),
  };
}

interface OpcionesLineas {
  x: string;
  valor: string;
  /** Campo que separa las series; sin él se dibuja una sola línea. */
  serie?: string;
  nombre?: string;
  sufijo?: string;
}

export function lineas(datos: Fila[], o: OpcionesLineas): EChartsOption {
  const estilo = {
    type: 'line' as const,
    symbol: 'circle',
    symbolSize: 7,
    lineStyle: { width: 2 },
  };
  let categorias: string[];
  let series: EChartsOption['series'];
  if (o.serie) {
    const pivote = pivotar(datos, o.x, o.serie, o.valor);
    categorias = pivote.categorias;
    series = pivote.series.map((serie) => ({
      ...estilo,
      name: serie,
      data: categorias.map((c) => pivote.valores.get(`${c}|${serie}`) ?? null),
    }));
  } else {
    categorias = datos.map((f) => texto(f, o.x));
    series = [{ ...estilo, name: o.nombre, data: datos.map((f) => valorDe(f, o.valor)) }];
  }
  return {
    ...base(),
    legend: o.serie ? { top: 0, type: 'scroll' } : undefined,
    grid: { left: 8, right: 24, top: o.serie ? 36 : 16, bottom: 8 },
    tooltip: {
      trigger: 'axis',
      confine: true,
      valueFormatter: (v) => formatearNumero(v, o.sufijo),
    },
    xAxis: ejeCategoria(categorias, { boundaryGap: false }),
    yAxis: { ...ejeValor(o.sufijo), scale: !!o.sufijo },
    series,
  };
}

export function anillo(datos: Fila[], categoria: string, valor: string): EChartsOption {
  return {
    color: COLORES,
    textStyle: { fontFamily: 'Roboto, sans-serif', color: TEXTO },
    tooltip: {
      trigger: 'item',
      formatter: (p) => {
        const d = p as unknown as { name: string; value: number; percent: number };
        return `${d.name}<br><b>${formatearNumero(d.value)}</b> (${formatearNumero(d.percent, '%')})`;
      },
    },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '45%'],
        itemStyle: { borderColor: SUPERFICIE, borderWidth: 2 },
        label: { formatter: '{d}%', color: TEXTO },
        data: datos.map((f) => ({ name: texto(f, categoria), value: valorDe(f, valor) })),
      },
    ],
  };
}

interface OpcionesCalor {
  x: string;
  y: string;
  valor: string;
  nombre: string;
  sufijo?: string;
}

/**
 * Mapa de calor de dos categorías. Sirve cuando hay demasiadas series para
 * distinguirlas por color (por ejemplo, carreras por año).
 */
export function mapaCalor(datos: Fila[], o: OpcionesCalor): EChartsOption {
  const xs = [...new Set(datos.map((f) => texto(f, o.x)))].sort();
  const ys = [...new Set(datos.map((f) => texto(f, o.y)))].sort();
  const puntos = datos.map((f) => [
    xs.indexOf(texto(f, o.x)),
    ys.indexOf(texto(f, o.y)),
    valorDe(f, o.valor),
  ]);
  const valores = puntos.map((p) => p[2]);
  return {
    textStyle: { fontFamily: 'Roboto, sans-serif', color: TEXTO },
    grid: { left: 8, right: 16, top: 8, bottom: 56 },
    tooltip: {
      confine: true,
      formatter: (p) => {
        const [x, y, v] = (p as unknown as { value: number[] }).value;
        return `${ys[y]} · ${xs[x]}<br>${o.nombre}: <b>${formatearNumero(v, o.sufijo)}</b>`;
      },
    },
    xAxis: ejeCategoria(xs, { splitArea: { show: false } }),
    yAxis: ejeCategoria(ys, { inverse: true }),
    visualMap: {
      min: Math.min(...valores),
      max: Math.max(...valores),
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      itemHeight: 160,
      calculable: false,
      text: [
        formatearNumero(Math.max(...valores), o.sufijo),
        formatearNumero(Math.min(...valores), o.sufijo),
      ],
      textStyle: { color: TEXTO },
      inRange: { color: ESCALA },
      formatter: (v) => formatearNumero(v, o.sufijo),
    },
    series: [
      {
        type: 'heatmap',
        name: o.nombre,
        data: puntos,
        itemStyle: { borderColor: SUPERFICIE, borderWidth: 2 },
        label: {
          show: puntos.length <= 80,
          formatter: (p) => formatearNumero((p.value as number[])[2]),
          fontSize: 11,
        },
      },
    ],
  };
}

/** Altura que necesita un mapa de calor según la cantidad de filas. */
export function alturaCalor(datos: Fila[], y: string): number {
  return new Set(datos.map((f) => f[y])).size * 30 + 90;
}

/**
 * Convierte filas (categoria, serie, valor) en una tabla cruzada. Si hay más
 * series que colores, las de menor total se agrupan en "OTRAS".
 */
function pivotar(datos: Fila[], categoria: string, serie: string, valor: string) {
  const totales = new Map<string, number>();
  datos.forEach((f) =>
    totales.set(texto(f, serie), (totales.get(texto(f, serie)) ?? 0) + valorDe(f, valor)),
  );
  const ordenadas = [...totales.keys()].sort();
  const visibles =
    ordenadas.length <= COLORES.length
      ? ordenadas
      : [...totales.entries()]
          .sort((a, b) => b[1] - a[1])
          .slice(0, COLORES.length - 1)
          .map(([nombre]) => nombre)
          .sort();
  const series = visibles.length < ordenadas.length ? [...visibles, 'OTRAS'] : visibles;

  const categorias = [...new Set(datos.map((f) => texto(f, categoria)))];
  const valores = new Map<string, number>();
  datos.forEach((f) => {
    const nombre = visibles.includes(texto(f, serie)) ? texto(f, serie) : 'OTRAS';
    const clave = `${texto(f, categoria)}|${nombre}`;
    valores.set(clave, (valores.get(clave) ?? 0) + valorDe(f, valor));
  });
  return { categorias, series, valores };
}
