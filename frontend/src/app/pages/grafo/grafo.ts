import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import type { ECElementEvent } from 'echarts';
import { NgxEchartsDirective } from 'ngx-echarts';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import {
  ConsultaGrafoInfo,
  NodoGrafo,
  OpcionesFiltro,
  ParametroCypher,
  ResultadoGrafo,
} from '../../core/modelos';
import { formatearCelda, TablaDatos } from '../dashboard/tabla-datos/tabla-datos';
import { NOMBRES_CATEGORIA, opcionesGrafo } from './opciones-grafo';

/** Mismo límite que ConsultaCypherService.LIMITE_MAXIMO en el backend. */
export const LIMITE_MAXIMO = 500;

/** Filtros de texto que se eligen de una lista en lugar de escribirse. */
const LISTA_PARAMETRO: Record<string, keyof OpcionesFiltro> = {
  departamento: 'departamentos',
  carrera: 'carreras',
  materia: 'materias',
  tipo: 'tiposInstitucion',
};

const ETIQUETA_PARAMETRO: Record<string, string> = {
  correlativo: 'Correlativo del aspirante',
  departamento: 'Departamento',
  carrera: 'Carrera objetivo',
  materia: 'Materia',
  tipo: 'Tipo de institución',
  limite: 'Límite',
};

/** Revisa los filtros antes de enviarlos; devuelve el mensaje de error o null. */
export function validarParametro(p: ParametroCypher, valor: string): string | null {
  const texto = valor.trim();
  if (!texto) {
    return p.requerido ? 'Este campo es obligatorio' : null;
  }
  if (p.tipo === 'entero') {
    const numero = Number(texto);
    if (!Number.isInteger(numero) || numero < 1 || numero > LIMITE_MAXIMO) {
      return `Debe ser un entero entre 1 y ${LIMITE_MAXIMO}`;
    }
  }
  return null;
}

@Component({
  selector: 'app-grafo',
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTabsModule,
    NgxEchartsDirective,
    TablaDatos,
  ],
  templateUrl: './grafo.html',
  styleUrl: './grafo.scss',
})
export class Grafo implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly consultas = signal<ConsultaGrafoInfo[]>([]);
  protected readonly opcionesFiltro = signal<OpcionesFiltro | null>(null);
  protected readonly errorCatalogo = signal(false);

  protected readonly seleccionada = signal<ConsultaGrafoInfo | null>(null);
  protected readonly valores = signal<Record<string, string>>({});
  protected readonly resultado = signal<ResultadoGrafo | null>(null);
  protected readonly ejecutando = signal(false);
  protected readonly errorConsulta = signal(false);
  protected readonly nodoActivo = signal<NodoGrafo | null>(null);

  protected readonly errores = computed(() => {
    const consulta = this.seleccionada();
    const errores: Record<string, string | null> = {};
    consulta?.parametros.forEach(
      (p) => (errores[p.nombre] = validarParametro(p, this.valores()[p.nombre] ?? '')),
    );
    return errores;
  });
  protected readonly valido = computed(() =>
    Object.values(this.errores()).every((e) => e === null),
  );

  protected readonly opciones = computed(() => {
    const r = this.resultado();
    return r && r.nodes.length > 0 ? opcionesGrafo(r) : null;
  });
  protected readonly detalleNodo = computed(() => {
    const nodo = this.nodoActivo();
    return nodo
      ? Object.entries(nodo.propiedades)
          .filter(([clave]) => clave !== 'clave')
          .map(([clave, valor]) => ({
            clave: clave.replace(/_/g, ' '),
            valor: formatearCelda(valor, clave),
          }))
      : [];
  });

  protected readonly nombresCategoria: Partial<Record<string, string>> = NOMBRES_CATEGORIA;
  protected readonly limiteMaximo = LIMITE_MAXIMO;

  ngOnInit(): void {
    this.cargarCatalogo();
  }

  protected cargarCatalogo(): void {
    this.errorCatalogo.set(false);
    forkJoin({
      consultas: this.api.get<ConsultaGrafoInfo[]>('grafo'),
      opciones: this.api.get<OpcionesFiltro>('buscar/opciones'),
    }).subscribe({
      next: ({ consultas, opciones }) => {
        this.consultas.set(consultas);
        this.opcionesFiltro.set(opciones);
        // Se abre con la primera consulta que no pide datos obligatorios
        const inicial =
          consultas.find((c) => c.parametros.every((p) => !p.requerido)) ?? consultas[0];
        if (inicial) {
          this.elegir(inicial.id);
        }
      },
      error: () => this.errorCatalogo.set(true),
    });
  }

  protected elegir(id: number): void {
    const consulta = this.consultas().find((c) => c.id === id) ?? null;
    this.seleccionada.set(consulta);
    this.valores.set(
      Object.fromEntries(
        (consulta?.parametros ?? []).map((p) => [p.nombre, p.valorPorDefecto ?? '']),
      ),
    );
    this.resultado.set(null);
    this.nodoActivo.set(null);
    this.errorConsulta.set(false);
    if (consulta && this.valido()) {
      this.ejecutar();
    }
  }

  protected cambiar(nombre: string, valor: string): void {
    this.valores.update((v) => ({ ...v, [nombre]: valor }));
  }

  protected ejecutar(): void {
    const consulta = this.seleccionada();
    if (!consulta || !this.valido() || this.ejecutando()) {
      return;
    }
    this.ejecutando.set(true);
    this.errorConsulta.set(false);
    this.nodoActivo.set(null);
    this.api.get<ResultadoGrafo>(`grafo/${consulta.id}`, this.valores()).subscribe({
      next: (resultado) => {
        this.resultado.set(resultado);
        this.ejecutando.set(false);
      },
      error: () => {
        this.errorConsulta.set(true);
        this.ejecutando.set(false);
      },
    });
  }

  protected limpiar(): void {
    const consulta = this.seleccionada();
    if (consulta) {
      this.elegir(consulta.id);
    }
  }

  protected alHacerClic(evento: ECElementEvent): void {
    const dato = evento.data as { nodo?: NodoGrafo } | undefined;
    if (evento.dataType === 'node' && dato?.nodo) {
      this.nodoActivo.set(dato.nodo);
    }
  }

  protected lista(p: ParametroCypher): string[] | null {
    const campo = LISTA_PARAMETRO[p.nombre];
    const opciones = this.opcionesFiltro();
    return campo && opciones ? (opciones[campo] as string[]) : null;
  }

  protected etiqueta(p: ParametroCypher): string {
    return ETIQUETA_PARAMETRO[p.nombre] ?? p.nombre;
  }
}
