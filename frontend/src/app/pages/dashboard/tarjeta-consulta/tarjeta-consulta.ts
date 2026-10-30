import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { NgxEchartsDirective } from 'ngx-echarts';
import { ApiService } from '../../../core/api.service';
import { ResultadoConsulta } from '../../../core/modelos';
import { ConfigConsulta } from '../consultas';
import { TablaDatos } from '../tabla-datos/tabla-datos';

type Vista = 'grafica' | 'tabla';

/** Ejecuta una consulta de MongoDB y la muestra como gráfica o tabla. */
@Component({
  selector: 'app-tarjeta-consulta',
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressBarModule,
    NgxEchartsDirective,
    TablaDatos,
  ],
  templateUrl: './tarjeta-consulta.html',
  styleUrl: './tarjeta-consulta.scss',
  host: { '[class.ancho]': 'config().ancho' },
})
export class TarjetaConsulta implements OnInit {
  private readonly api = inject(ApiService);

  readonly config = input.required<ConfigConsulta>();

  protected readonly resultado = signal<ResultadoConsulta | null>(null);
  protected readonly cargando = signal(false);
  protected readonly error = signal(false);
  protected readonly vista = signal<Vista>('grafica');

  protected readonly tieneGrafica = computed(() => !!this.config().grafica);
  protected readonly opciones = computed(() => {
    const r = this.resultado();
    const grafica = this.config().grafica;
    return r && grafica && r.total > 0 ? grafica(r.datos) : null;
  });
  protected readonly filasTabla = computed(() => {
    const datos = this.resultado()?.datos ?? [];
    return this.config().filas?.(datos) ?? datos;
  });
  protected readonly altura = computed(() => {
    const r = this.resultado();
    return r ? (this.config().altura?.(r.datos) ?? 320) : 320;
  });

  ngOnInit(): void {
    this.consultar();
  }

  protected consultar(): void {
    this.cargando.set(true);
    this.error.set(false);
    this.api.get<ResultadoConsulta>(`mongo/${this.config().id}`).subscribe({
      next: (resultado) => {
        this.resultado.set(resultado);
        this.cargando.set(false);
      },
      // El interceptor ya muestra el mensaje; aquí solo se deja la opción de reintentar
      error: () => {
        this.error.set(true);
        this.cargando.set(false);
      },
    });
  }
}
