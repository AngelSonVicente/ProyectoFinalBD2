import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../../core/api.service';
import { Fila, ResultadoConsulta } from '../../../core/modelos';
import { formatearCelda, TablaDatos } from '../tabla-datos/tabla-datos';

interface EvaluacionMateria {
  fecha_asignacion: string;
  intento: number;
  aprobado: boolean;
}

/** Datos que se muestran en la ficha del aspirante (Q15), en este orden. */
const CAMPOS_PERFIL: [string, string][] = [
  ['correlativo_aspirante', 'Correlativo'],
  ['sexo', 'Sexo'],
  ['anio_nacimiento', 'Año de nacimiento'],
  ['tipo_institucion', 'Tipo de institución'],
  ['departamento', 'Departamento'],
  ['municipio', 'Municipio'],
  ['carreras', 'Carreras'],
  ['materias', 'Materias'],
  ['total_evaluaciones', 'Evaluaciones'],
  ['evaluaciones_aprobadas', 'Evaluaciones aprobadas'],
];

/**
 * Q15 (historial completo) y Q11 (desempeño por materia) reciben el mismo
 * correlativo, así que se consultan juntas desde un solo campo.
 */
@Component({
  selector: 'app-consulta-aspirante',
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatTableModule,
    TablaDatos,
  ],
  templateUrl: './consulta-aspirante.html',
  styleUrl: './consulta-aspirante.scss',
})
export class ConsultaAspirante {
  private readonly api = inject(ApiService);

  protected readonly correlativo = signal('');
  protected readonly cargando = signal(false);
  protected readonly historial = signal<ResultadoConsulta | null>(null);
  protected readonly desempeno = signal<ResultadoConsulta | null>(null);
  /** Correlativo de la última búsqueda, para el mensaje de "no encontrado". */
  protected readonly buscado = signal('');

  protected readonly perfil = computed(() => this.historial()?.datos[0] ?? null);
  protected readonly datosPerfil = computed(() => {
    const perfil = this.perfil();
    return perfil
      ? CAMPOS_PERFIL.map(([campo, etiqueta]) => ({
          etiqueta,
          valor: formatearCelda(perfil[campo], campo),
        }))
      : [];
  });
  protected readonly evaluaciones = computed(
    () => (this.perfil()?.['evaluaciones'] as Fila[] | undefined) ?? [],
  );

  protected readonly columnasMateria = ['materia', 'intentos', 'aprobada', 'evaluaciones'];

  protected consultar(): void {
    const correlativo = this.correlativo().trim();
    if (!correlativo || this.cargando()) {
      return;
    }
    this.cargando.set(true);
    forkJoin({
      historial: this.api.get<ResultadoConsulta>('mongo/15', { correlativo }),
      desempeno: this.api.get<ResultadoConsulta>('mongo/11', { correlativo }),
    }).subscribe({
      next: ({ historial, desempeno }) => {
        this.historial.set(historial);
        this.desempeno.set(desempeno);
        this.buscado.set(correlativo);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  protected readonly formatear = formatearCelda;

  protected intentos(fila: Fila): EvaluacionMateria[] {
    return (fila['evaluaciones'] as EvaluacionMateria[] | undefined) ?? [];
  }
}
