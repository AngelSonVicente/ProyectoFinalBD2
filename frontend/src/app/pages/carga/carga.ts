import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../../core/api.service';
import { CargaHistorial, EstadoCarga, ResumenCarga } from '../../core/modelos';
import { Rechazados } from './rechazados/rechazados';

/** Mismo límite que spring.servlet.multipart.max-file-size en el backend. */
const TAMANO_MAXIMO = 200 * 1024 * 1024;

type Fase = 'inactivo' | 'subiendo' | 'procesando';

@Component({
  selector: 'app-carga',
  imports: [
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
    Rechazados,
  ],
  templateUrl: './carga.html',
  styleUrl: './carga.scss',
})
export class Carga implements OnInit {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly archivos = signal<File[]>([]);
  protected readonly arrastrando = signal(false);
  protected readonly fase = signal<Fase>('inactivo');
  protected readonly progreso = signal(0);
  protected readonly resultados = signal<ResumenCarga[]>([]);
  protected readonly historial = signal<CargaHistorial[]>([]);
  protected readonly cargandoHistorial = signal(false);
  protected readonly errorHistorial = signal(false);
  /** Carga cuyos rechazados se están mostrando. */
  protected readonly seleccion = signal<{ id: string; archivo: string } | null>(null);

  protected readonly ocupado = computed(() => this.fase() !== 'inactivo');
  protected readonly tamanoTotal = computed(() =>
    this.archivos().reduce((total, archivo) => total + archivo.size, 0),
  );

  protected readonly columnasResultado = [
    'archivo',
    'estado',
    'leidos',
    'procesados',
    'rechazados',
    'duplicados',
    'tiempo',
    'acciones',
  ];
  protected readonly columnasHistorial = [
    'fecha',
    'archivo',
    'estado',
    'leidos',
    'procesados',
    'rechazados',
    'tiempo',
    'acciones',
  ];

  ngOnInit(): void {
    this.cargarHistorial();
  }

  protected alSeleccionar(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    this.agregar(Array.from(entrada.files ?? []));
    // Permite volver a elegir el mismo archivo después de quitarlo
    entrada.value = '';
  }

  protected alArrastrar(evento: DragEvent, encima: boolean): void {
    evento.preventDefault();
    if (!this.ocupado()) {
      this.arrastrando.set(encima);
    }
  }

  protected alSoltar(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastrando.set(false);
    if (!this.ocupado()) {
      this.agregar(Array.from(evento.dataTransfer?.files ?? []));
    }
  }

  protected quitar(archivo: File): void {
    this.archivos.update((lista) => lista.filter((a) => a !== archivo));
  }

  protected subir(): void {
    const archivos = this.archivos();
    if (archivos.length === 0 || this.ocupado()) {
      return;
    }
    this.fase.set('subiendo');
    this.progreso.set(0);
    this.seleccion.set(null);

    this.api.subirArchivos<ResumenCarga[]>('cargas', archivos).subscribe({
      next: (evento) => {
        if (evento.type === HttpEventType.UploadProgress) {
          const porcentaje = evento.total ? Math.round((100 * evento.loaded) / evento.total) : 0;
          this.progreso.set(porcentaje);
          // Ya se envió todo: el backend está procesando y guardando en las dos bases
          if (porcentaje === 100) {
            this.fase.set('procesando');
          }
        } else if (evento.type === HttpEventType.Response) {
          const resultados = evento.body ?? [];
          this.resultados.set(resultados);
          this.archivos.set([]);
          this.fase.set('inactivo');
          this.avisar(resultados);
          this.cargarHistorial();
        }
      },
      // El interceptor ya mostró el mensaje; aquí solo se libera la pantalla
      error: () => this.fase.set('inactivo'),
    });
  }

  protected verRechazados(id: string | null, archivo: string): void {
    if (id) {
      this.seleccion.set({ id, archivo });
    }
  }

  protected cargarHistorial(): void {
    this.cargandoHistorial.set(true);
    this.errorHistorial.set(false);
    this.api.get<CargaHistorial[]>('cargas').subscribe({
      next: (historial) => {
        this.historial.set(historial);
        this.cargandoHistorial.set(false);
      },
      error: () => {
        this.errorHistorial.set(true);
        this.cargandoHistorial.set(false);
      },
    });
  }

  protected etiqueta(estado: EstadoCarga): string {
    return {
      EN_PROCESO: 'En proceso',
      COMPLETADO: 'Completado',
      COMPLETADO_CON_RECHAZOS: 'Con rechazos',
      FALLIDO: 'Fallido',
    }[estado];
  }

  protected segundos(ms: number | undefined): string {
    return ms === undefined ? '—' : `${(ms / 1000).toFixed(1)} s`;
  }

  protected tamano(bytes: number): string {
    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  private agregar(nuevos: File[]): void {
    const invalidos: string[] = [];
    const validos = nuevos.filter((archivo) => {
      if (!archivo.name.toLowerCase().endsWith('.csv')) {
        invalidos.push(`${archivo.name} no es un archivo CSV`);
        return false;
      }
      if (archivo.size > TAMANO_MAXIMO) {
        invalidos.push(`${archivo.name} supera los 200 MB`);
        return false;
      }
      return true;
    });

    // Evita agregar dos veces el mismo archivo
    this.archivos.update((lista) => [
      ...lista,
      ...validos.filter((n) => !lista.some((a) => a.name === n.name && a.size === n.size)),
    ]);

    if (invalidos.length > 0) {
      this.snackBar.open(invalidos.join('. '), 'Cerrar', { duration: 6000 });
    }
  }

  private avisar(resultados: ResumenCarga[]): void {
    const fallidos = resultados.filter((r) => r.estado === 'FALLIDO').length;
    const mensaje =
      fallidos === 0
        ? `Carga terminada: ${resultados.length} archivo(s) procesado(s)`
        : `${fallidos} de ${resultados.length} archivo(s) no se pudieron cargar. Revise el detalle.`;
    this.snackBar.open(mensaje, 'Cerrar', { duration: 6000 });
  }
}
