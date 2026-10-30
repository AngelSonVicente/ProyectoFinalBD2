import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { CargaHistorial, ResultadoConsulta } from '../../core/modelos';
import { ConsultaAspirante } from './consulta-aspirante/consulta-aspirante';
import { SECCIONES } from './consultas';
import { TarjetaConsulta } from './tarjeta-consulta/tarjeta-consulta';

interface Indicadores {
  aspirantes: number;
  evaluaciones: number;
  aprobadas: number;
  cargas: number;
  rechazados: number;
}

const suma = (filas: Record<string, unknown>[], campo: string) =>
  filas.reduce((total, fila) => total + Number(fila[campo] ?? 0), 0);

@Component({
  selector: 'app-dashboard',
  imports: [
    DecimalPipe,
    PercentPipe,
    MatButtonModule,
    MatIconModule,
    MatTabsModule,
    RouterLink,
    ConsultaAspirante,
    TarjetaConsulta,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly secciones = SECCIONES;
  protected readonly indicadores = signal<Indicadores | null>(null);
  protected readonly errorIndicadores = signal(false);

  ngOnInit(): void {
    this.cargarIndicadores();
  }

  /**
   * Los totales salen de consultas que ya existen: Q12 cuenta aspirantes en la
   * colección aspirantes y Q20 cuenta evaluaciones aprobadas y no aprobadas.
   */
  protected cargarIndicadores(): void {
    this.errorIndicadores.set(false);
    forkJoin({
      sexo: this.api.get<ResultadoConsulta>('mongo/12'),
      anios: this.api.get<ResultadoConsulta>('mongo/20'),
      cargas: this.api.get<CargaHistorial[]>('cargas'),
    }).subscribe({
      next: ({ sexo, anios, cargas }) =>
        this.indicadores.set({
          aspirantes: suma(sexo.datos, 'aspirantes'),
          evaluaciones: suma(anios.datos, 'total'),
          aprobadas: suma(anios.datos, 'aprobadas'),
          cargas: cargas.length,
          rechazados: cargas.reduce((total, c) => total + (c.rechazados ?? 0), 0),
        }),
      error: () => this.errorIndicadores.set(true),
    });
  }
}
