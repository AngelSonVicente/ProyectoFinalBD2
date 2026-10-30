import { DecimalPipe, KeyValuePipe } from '@angular/common';
import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ApiService } from '../../../core/api.service';
import { PaginaRechazados } from '../../../core/modelos';

/** Tabla paginada con los registros rechazados de una carga y su causa. */
@Component({
  selector: 'app-rechazados',
  imports: [
    DecimalPipe,
    KeyValuePipe,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
  ],
  templateUrl: './rechazados.html',
  styleUrl: './rechazados.scss',
})
export class Rechazados {
  private readonly api = inject(ApiService);

  readonly cargaId = input.required<string>();
  readonly archivo = input.required<string>();

  protected readonly columnas = ['fila', 'causas', 'detalle'];
  protected readonly pagina = signal(0);
  protected readonly tamano = signal(25);
  protected readonly resultado = signal<PaginaRechazados | null>(null);
  protected readonly cargando = signal(false);
  protected readonly expandida = signal<number | null>(null);

  constructor() {
    // Al cambiar de carga se vuelve a la primera página
    effect(() => {
      this.cargaId();
      untracked(() => {
        this.pagina.set(0);
        this.expandida.set(null);
        this.consultar();
      });
    });
  }

  protected cambiarPagina(evento: PageEvent): void {
    this.pagina.set(evento.pageIndex);
    this.tamano.set(evento.pageSize);
    this.expandida.set(null);
    this.consultar();
  }

  protected alternar(fila: number): void {
    this.expandida.update((actual) => (actual === fila ? null : fila));
  }

  // Conserva el orden de las columnas del CSV
  protected readonly sinOrden = () => 0;

  private consultar(): void {
    this.cargando.set(true);
    this.api
      .get<PaginaRechazados>(`cargas/${this.cargaId()}/rechazados`, {
        pagina: this.pagina(),
        tamano: this.tamano(),
      })
      .subscribe({
        next: (resultado) => {
          this.resultado.set(resultado);
          this.cargando.set(false);
        },
        error: () => this.cargando.set(false),
      });
  }
}
