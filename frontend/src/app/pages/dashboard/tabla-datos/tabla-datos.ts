import { Component, computed, effect, input, viewChild } from '@angular/core';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { Fila } from '../../../core/modelos';
import { formatearNumero } from '../graficas';

const FECHA_ISO = /^\d{4}-\d{2}-\d{2}T/;
// Las fechas se guardan a medianoche UTC; sin timeZone se mostrarían un día antes en Guatemala
const fecha = new Intl.DateTimeFormat('es-GT', { dateStyle: 'medium', timeZone: 'UTC' });
/** Campos numéricos que funcionan como etiqueta (años, edad): sin separador de miles y al inicio. */
const CAMPO_ETIQUETA = /^(anio|edad)/;

/** Convierte cualquier valor de un documento de MongoDB en texto legible. */
export function formatearCelda(valor: unknown, campo = ''): string {
  if (valor === null || valor === undefined || valor === '') {
    return '—';
  }
  if (typeof valor === 'boolean') {
    return valor ? 'Sí' : 'No';
  }
  if (typeof valor === 'number') {
    return CAMPO_ETIQUETA.test(campo) ? String(valor) : formatearNumero(valor);
  }
  if (typeof valor === 'string') {
    return FECHA_ISO.test(valor) ? fecha.format(new Date(valor)) : valor;
  }
  if (Array.isArray(valor)) {
    return valor
      .map((v) => formatearCelda(v, campo))
      .join(valor.some((v) => typeof v === 'object') ? '; ' : ', ');
  }
  return Object.entries(valor as object)
    .map(([clave, v]) => formatearCelda(v, clave))
    .join(' · ');
}

/** "porcentaje_aprobacion" → "Porcentaje aprobacion", "anio_ingreso" → "Año ingreso". */
function titulo(campo: string): string {
  const texto = campo.replace(/_/g, ' ').replace(/\banio\b/, 'año');
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/**
 * Tabla genérica para el resultado de una agregación: las columnas salen de
 * los campos de los documentos, con orden y paginación en el navegador.
 */
@Component({
  selector: 'app-tabla-datos',
  imports: [MatTableModule, MatSortModule, MatPaginatorModule],
  templateUrl: './tabla-datos.html',
  styleUrl: './tabla-datos.scss',
})
export class TablaDatos {
  readonly datos = input.required<Fila[]>();
  readonly ocultar = input<string[]>([]);
  readonly tamano = input(10);

  private readonly paginador = viewChild(MatPaginator);
  private readonly orden = viewChild(MatSort);

  protected readonly fuente = new MatTableDataSource<Fila>([]);

  protected readonly columnas = computed(() => {
    const campos = new Set<string>();
    this.datos().forEach((fila) => Object.keys(fila).forEach((campo) => campos.add(campo)));
    // MongoDB no respeta el orden del $project: primero van los textos, luego los números y al final listas y fechas
    const primera = this.datos()[0] ?? {};
    const rango = (campo: string) => {
      const valor = primera[campo];
      if ((typeof valor === 'string' && !FECHA_ISO.test(valor)) || CAMPO_ETIQUETA.test(campo)) {
        return 0;
      }
      return typeof valor === 'number' ? 1 : 2;
    };
    return [...campos]
      .filter((campo) => !this.ocultar().includes(campo))
      .sort((a, b) => rango(a) - rango(b))
      .map((campo) => ({ campo, titulo: titulo(campo) }));
  });
  protected readonly campos = computed(() => this.columnas().map((c) => c.campo));
  protected readonly paginada = computed(() => this.datos().length > this.tamano());

  protected readonly formatear = formatearCelda;
  protected readonly esNumero = (valor: unknown, campo: string) =>
    typeof valor === 'number' && !CAMPO_ETIQUETA.test(campo);

  constructor() {
    this.fuente.sortingDataAccessor = (fila, campo) => {
      const valor = fila[campo];
      return typeof valor === 'number' ? valor : formatearCelda(valor);
    };
    effect(() => {
      this.fuente.data = this.datos();
      this.fuente.paginator = this.paginada() ? (this.paginador() ?? null) : null;
      this.fuente.sort = this.orden() ?? null;
    });
  }
}
