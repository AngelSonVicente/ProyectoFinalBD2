import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideEchartsCore } from 'ngx-echarts';
import { environment } from '../../../environments/environment';
import {
  ConsultaGrafoInfo,
  OpcionesFiltro,
  ParametroCypher,
  ResultadoGrafo,
} from '../../core/modelos';
import { Grafo, validarParametro } from './grafo';
import { opcionesGrafo } from './opciones-grafo';

const limite: ParametroCypher = {
  nombre: 'limite',
  tipo: 'entero',
  requerido: false,
  valorPorDefecto: '15',
  descripcion: 'Cantidad',
};

const catalogo: ConsultaGrafoInfo[] = [
  {
    id: 1,
    titulo: 'Red de un aspirante',
    descripcion: '',
    parametros: [
      {
        nombre: 'correlativo',
        tipo: 'correlativo',
        requerido: true,
        valorPorDefecto: null,
        descripcion: '',
      },
    ],
    cypher: 'MATCH p = ...',
  },
  {
    id: 2,
    titulo: 'Carreras más buscadas por departamento',
    descripcion: '',
    parametros: [
      {
        nombre: 'departamento',
        tipo: 'texto',
        requerido: false,
        valorPorDefecto: null,
        descripcion: '',
      },
      limite,
    ],
    cypher: 'MATCH (d:Departamento) ...',
  },
];

const opciones: OpcionesFiltro = {
  carreras: [],
  materias: [],
  departamentos: ['GUATEMALA', 'PETEN'],
  municipios: [],
  tiposInstitucion: [],
  aniosIngreso: [],
};

const resultado: ResultadoGrafo = {
  id: 2,
  titulo: 'Carreras más buscadas por departamento',
  descripcion: '',
  parametros: { departamento: null, limite: 15 },
  tiempoMs: 20,
  nodes: [
    {
      id: 'd1',
      nombre: 'GUATEMALA',
      categoria: 'Departamento',
      propiedades: { nombre: 'GUATEMALA' },
    },
    {
      id: 'c1',
      nombre: 'INGENIERIA CIVIL',
      categoria: 'Carrera',
      propiedades: { nombre: 'INGENIERIA CIVIL' },
    },
    {
      id: 'c2',
      nombre: 'INGENIERIA QUIMICA',
      categoria: 'Carrera',
      propiedades: { nombre: 'INGENIERIA QUIMICA' },
    },
  ],
  links: [
    { id: 'l1', source: 'd1', target: 'c1', tipo: 'ASPIRANTES', propiedades: { aspirantes: 100 } },
    { id: 'l2', source: 'd1', target: 'c2', tipo: 'ASPIRANTES', propiedades: { aspirantes: 300 } },
  ],
  filas: [{ departamento: 'GUATEMALA', carrera: 'INGENIERIA CIVIL', aspirantes: 100 }],
};

describe('opcionesGrafo', () => {
  it('arma una serie graph con layout de fuerzas y grosor según la relación calculada', () => {
    const serie = (opcionesGrafo(resultado).series as Record<string, unknown>[])[0];
    expect(serie['type']).toBe('graph');
    expect(serie['layout']).toBe('force');

    const enlaces = serie['links'] as { lineStyle: { width: number } }[];
    expect(enlaces.map((e) => e.lineStyle.width)).toEqual([1, 8]);

    const nodos = serie['data'] as { category: number; symbolSize: number }[];
    // El departamento tiene dos conexiones: es el nodo más grande
    expect(nodos[0].symbolSize).toBeGreaterThan(nodos[1].symbolSize);
  });

  it('dibuja punteados los intentos no aprobados', () => {
    const conIntento: ResultadoGrafo = {
      ...resultado,
      links: [
        { id: 'r1', source: 'd1', target: 'c1', tipo: 'REALIZO', propiedades: { aprobado: false } },
      ],
    };
    const serie = (opcionesGrafo(conIntento).series as Record<string, unknown>[])[0];
    expect((serie['links'] as { lineStyle: { type: string } }[])[0].lineStyle.type).toBe('dashed');
  });
});

describe('validarParametro', () => {
  it('exige los parámetros requeridos y limita el entero entre 1 y 500', () => {
    expect(validarParametro(catalogo[0].parametros[0], ' ')).toContain('obligatorio');
    expect(validarParametro(limite, '')).toBeNull();
    expect(validarParametro(limite, '15')).toBeNull();
    expect(validarParametro(limite, '0')).toContain('entre 1 y 500');
    expect(validarParametro(limite, '501')).toContain('entre 1 y 500');
    expect(validarParametro(limite, '2.5')).toContain('entre 1 y 500');
  });
});

describe('Grafo', () => {
  // jsdom no trae ResizeObserver, que ngx-echarts usa para ajustar el tamaño del gráfico
  globalThis.ResizeObserver ??= class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  };

  let fixture: ComponentFixture<Grafo>;
  let http: HttpTestingController;
  const api = environment.apiUrl;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Grafo],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideEchartsCore({ echarts: () => import('echarts') }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Grafo);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${api}/grafo`).flush(catalogo);
    http.expectOne(`${api}/buscar/opciones`).flush(opciones);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('abre la primera consulta sin parámetros obligatorios y la ejecuta con sus valores por defecto', () => {
    const peticion = http.expectOne((r) => r.url === `${api}/grafo/2`);
    // El departamento vacío no se envía; el límite usa el valor por defecto
    expect(peticion.request.params.has('departamento')).toBe(false);
    expect(peticion.request.params.get('limite')).toBe('15');
    peticion.flush(resultado);
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('3 nodos');
    expect(texto).toContain('2 relaciones');
    expect(texto).toContain('Filas del resultado (1)');
  });

  it('muestra un aviso cuando la consulta no devuelve nodos', () => {
    http
      .expectOne((r) => r.url === `${api}/grafo/2`)
      .flush({ ...resultado, nodes: [], links: [], filas: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('no devolvió nodos');
  });
});
