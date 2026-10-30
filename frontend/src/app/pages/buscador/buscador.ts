import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { FiltrosBusqueda, OpcionesFiltro, PaginaBusqueda } from '../../core/modelos';
import { formatearCelda } from '../dashboard/tabla-datos/tabla-datos';

type CampoFiltro = keyof FiltrosBusqueda;

/** Filtros de texto con sugerencias: el backend busca que el valor esté contenido, no que sea igual. */
interface CampoTexto {
  campo: CampoFiltro;
  etiqueta: string;
  sugerencias: (opciones: OpcionesFiltro, filtros: FiltrosBusqueda) => string[];
}

const ETIQUETAS: Record<CampoFiltro, string> = {
  aspirante: 'Correlativo',
  carrera: 'Carrera',
  institucion: 'Tipo de institución',
  materia: 'Materia',
  municipio: 'Municipio',
  departamento: 'Departamento',
  anio: 'Año de ingreso',
  aprobado: 'Resultado',
};

const CAMPOS: CampoFiltro[] = Object.keys(ETIQUETAS) as CampoFiltro[];

const TAMANOS = [10, 20, 50, 100];

/** Quita tildes y pasa a mayúsculas, como el ETL, para comparar sugerencias. */
function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase().trim();
}

/** Deja solo los filtros con valor, para no enviar ni mostrar campos vacíos. */
export function filtrosConValor(filtros: FiltrosBusqueda): FiltrosBusqueda {
  return Object.fromEntries(
    Object.entries(filtros).filter(([, valor]) => valor !== undefined && `${valor}`.trim() !== ''),
  ) as FiltrosBusqueda;
}

@Component({
  selector: 'app-buscador',
  imports: [
    DecimalPipe,
    MatAutocompleteModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './buscador.html',
  styleUrl: './buscador.scss',
})
export class Buscador implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);

  /** Lo que el usuario está escribiendo. */
  protected readonly borrador = signal<FiltrosBusqueda>({});
  /** Filtros de la última búsqueda enviada. */
  protected readonly aplicados = signal<FiltrosBusqueda>({});
  protected readonly pagina = signal(0);
  protected readonly tamano = signal(20);

  protected readonly opciones = signal<OpcionesFiltro | null>(null);
  protected readonly resultado = signal<PaginaBusqueda | null>(null);
  protected readonly cargando = signal(false);
  protected readonly error = signal(false);

  protected readonly camposTexto: CampoTexto[] = [
    { campo: 'carrera', etiqueta: ETIQUETAS.carrera, sugerencias: (o) => o.carreras },
    { campo: 'materia', etiqueta: ETIQUETAS.materia, sugerencias: (o) => o.materias },
    {
      campo: 'departamento',
      etiqueta: ETIQUETAS.departamento,
      sugerencias: (o) => o.departamentos,
    },
    {
      campo: 'municipio',
      etiqueta: ETIQUETAS.municipio,
      // Si ya se eligió un departamento, solo se sugieren sus municipios
      sugerencias: (o, f) =>
        o.municipios
          .filter(
            (m) =>
              !f.departamento || normalizar(m.departamento).includes(normalizar(f.departamento)),
          )
          .map((m) => m.municipio),
    },
    {
      campo: 'institucion',
      etiqueta: ETIQUETAS.institucion,
      sugerencias: (o) => o.tiposInstitucion,
    },
  ];

  protected readonly columnas = [
    'correlativo',
    'materia',
    'intento',
    'fecha',
    'resultado',
    'carrera',
    'institucion',
    'procedencia',
    'ingreso',
    'edad',
    'sexo',
  ];
  protected readonly tamanos = TAMANOS;

  protected readonly chips = computed(() =>
    Object.entries(this.aplicados()).map(([campo, valor]) => ({
      campo: campo as CampoFiltro,
      texto: `${ETIQUETAS[campo as CampoFiltro]}: ${campo === 'aprobado' ? (valor === 'true' ? 'Aprobado' : 'No aprobado') : valor}`,
    })),
  );
  protected readonly hayCambios = computed(
    () => JSON.stringify(filtrosConValor(this.borrador())) !== JSON.stringify(this.aplicados()),
  );

  protected readonly formatear = formatearCelda;

  ngOnInit(): void {
    // Los filtros viven en la URL: al recargar o compartir el enlace se repite la búsqueda
    const parametros = this.ruta.snapshot.queryParamMap;
    const filtros = filtrosConValor(
      Object.fromEntries(CAMPOS.map((c) => [c, parametros.get(c) ?? undefined])) as FiltrosBusqueda,
    );
    const pagina = Number(parametros.get('pagina'));
    const tamano = Number(parametros.get('tamano'));
    this.borrador.set(filtros);
    this.aplicados.set(filtros);
    this.pagina.set(Number.isInteger(pagina) && pagina > 0 ? pagina : 0);
    this.tamano.set(TAMANOS.includes(tamano) ? tamano : 20);

    this.api.get<OpcionesFiltro>('buscar/opciones').subscribe({
      next: (opciones) => this.opciones.set(opciones),
      // Sin opciones los campos siguen funcionando, solo sin sugerencias
      error: () => this.opciones.set(null),
    });
    this.consultar();
  }

  protected cambiar(campo: CampoFiltro, valor: string): void {
    this.borrador.update((f) => ({ ...f, [campo]: valor }));
  }

  protected sugerencias(c: CampoTexto): string[] {
    const opciones = this.opciones();
    if (!opciones) {
      return [];
    }
    const texto = normalizar(this.borrador()[c.campo] ?? '');
    return c.sugerencias(opciones, this.borrador()).filter((s) => normalizar(s).includes(texto));
  }

  protected buscar(): void {
    this.aplicados.set(filtrosConValor(this.borrador()));
    this.pagina.set(0);
    this.consultar();
  }

  protected limpiar(): void {
    this.borrador.set({});
    this.buscar();
  }

  protected quitar(campo: CampoFiltro): void {
    this.borrador.update((f) => ({ ...f, [campo]: undefined }));
    this.buscar();
  }

  /** Al hacer clic en un correlativo se ven todas las evaluaciones de ese aspirante. */
  protected verAspirante(correlativo: string): void {
    this.borrador.set({ aspirante: correlativo });
    this.buscar();
  }

  protected cambiarPagina(evento: PageEvent): void {
    this.pagina.set(evento.pageIndex);
    this.tamano.set(evento.pageSize);
    this.consultar();
  }

  protected consultar(): void {
    const filtros = this.aplicados();
    const pagina = this.pagina();
    const tamano = this.tamano();
    this.router.navigate([], {
      relativeTo: this.ruta,
      queryParams: {
        ...filtros,
        pagina: pagina || undefined,
        tamano: tamano === 20 ? undefined : tamano,
      },
      replaceUrl: true,
    });

    this.cargando.set(true);
    this.error.set(false);
    this.api.get<PaginaBusqueda>('buscar', { ...filtros, pagina, tamano }).subscribe({
      next: (resultado) => {
        this.resultado.set(resultado);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set(true);
        this.cargando.set(false);
      },
    });
  }
}
