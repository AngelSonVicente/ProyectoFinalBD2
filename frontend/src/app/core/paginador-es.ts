import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

const numero = new Intl.NumberFormat('es-GT');

/** Textos del paginador de Angular Material en español. */
@Injectable()
export class PaginadorEs extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Por página';
  override nextPageLabel = 'Página siguiente';
  override previousPageLabel = 'Página anterior';
  override firstPageLabel = 'Primera página';
  override lastPageLabel = 'Última página';

  override getRangeLabel = (pagina: number, tamano: number, total: number): string => {
    if (total === 0) {
      return '0 de 0';
    }
    const inicio = pagina * tamano;
    const fin = Math.min(inicio + tamano, total);
    return `${numero.format(inicio + 1)} – ${numero.format(fin)} de ${numero.format(total)}`;
  };
}
