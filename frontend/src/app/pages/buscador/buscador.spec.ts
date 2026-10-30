import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { Evaluacion, OpcionesFiltro, PaginaBusqueda } from '../../core/modelos';
import { Buscador, filtrosConValor } from './buscador';

const opciones: OpcionesFiltro = {
  carreras: ['INGENIERIA CIVIL'],
  materias: ['FISICA'],
  departamentos: ['GUATEMALA'],
  municipios: [{ departamento: 'GUATEMALA', municipio: 'MIXCO' }],
  tiposInstitucion: ['PUBLICO'],
  aniosIngreso: [2024, 2025],
};

const evaluacion: Evaluacion = {
  _id: 'asp1|FISICA|2024-07-18|1|2025',
  correlativo_aspirante: 'asp1',
  fecha_asignacion: '2024-07-18T00:00:00.000Z',
  sexo: 'F',
  anio_nacimiento: 2008,
  edad: 17,
  materia: 'FISICA',
  intento: 1,
  anio_ingreso: 2025,
  aprobado: false,
  carrera_objetivo: 'INGENIERIA CIVIL',
  departamento: 'GUATEMALA',
  municipio: 'MIXCO',
  tipo_institucion: 'PUBLICO',
  archivo: 'datos.csv',
};

const pagina = (datos: Evaluacion[], total = datos.length): PaginaBusqueda => ({
  total,
  pagina: 0,
  tamano: 20,
  totalPaginas: Math.ceil(total / 20),
  filtros: {},
  tiempoMs: 5,
  datos,
});

describe('filtrosConValor', () => {
  it('descarta los filtros vacíos', () => {
    expect(
      filtrosConValor({ carrera: 'civil', materia: '', anio: '  ', aprobado: undefined }),
    ).toEqual({
      carrera: 'civil',
    });
  });
});

describe('Buscador', () => {
  let fixture: ComponentFixture<Buscador>;
  let http: HttpTestingController;
  const api = environment.apiUrl;
  const texto = () => fixture.nativeElement.textContent as string;

  const iniciar = async (query: Record<string, string> = {}) => {
    await TestBed.configureTestingModule({
      imports: [Buscador],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Buscador);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${api}/buscar/opciones`).flush(opciones);
  };

  afterEach(() => http.verify());

  it('busca sin filtros al entrar y muestra la tabla', async () => {
    await iniciar();
    const peticion = http.expectOne((r) => r.url === `${api}/buscar`);
    expect(peticion.request.params.keys().sort()).toEqual(['pagina', 'tamano']);
    peticion.flush(pagina([evaluacion], 1));
    fixture.detectChanges();

    expect(texto()).toContain('1 evaluación');
    expect(texto()).toContain('asp1');
    expect(texto()).toContain('No aprobado');
  });

  it('toma los filtros y la página de la URL', async () => {
    await iniciar({ carrera: 'civil', aprobado: 'false', pagina: '2' });
    const peticion = http.expectOne((r) => r.url === `${api}/buscar`);
    expect(peticion.request.params.get('carrera')).toBe('civil');
    expect(peticion.request.params.get('aprobado')).toBe('false');
    expect(peticion.request.params.get('pagina')).toBe('2');
    peticion.flush(pagina([evaluacion], 100));
    fixture.detectChanges();

    expect(texto()).toContain('Carrera: civil');
    expect(texto()).toContain('Resultado: No aprobado');
  });

  it('al quitar un filtro vuelve a buscar desde la primera página y actualiza la URL', async () => {
    await iniciar({ carrera: 'civil', materia: 'fisica', pagina: '3' });
    http.expectOne((r) => r.url === `${api}/buscar`).flush(pagina([evaluacion], 100));
    fixture.detectChanges();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate');

    const quitar = fixture.nativeElement.querySelector(
      '[aria-label="Quitar Carrera: civil"]',
    ) as HTMLButtonElement;
    quitar.click();
    const peticion = http.expectOne((r) => r.url === `${api}/buscar`);
    expect(peticion.request.params.has('carrera')).toBe(false);
    expect(peticion.request.params.get('materia')).toBe('fisica');
    expect(peticion.request.params.get('pagina')).toBe('0');
    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({ materia: 'fisica', pagina: undefined }),
      }),
    );
    peticion.flush(pagina([evaluacion], 50));
  });

  it('distingue entre filtros sin resultados y base vacía', async () => {
    await iniciar({ materia: 'xyz' });
    http.expectOne((r) => r.url === `${api}/buscar`).flush(pagina([]));
    fixture.detectChanges();
    expect(texto()).toContain('No hay evaluaciones con esos filtros');

    (fixture.nativeElement.querySelector('.estado button') as HTMLButtonElement).click();
    http.expectOne((r) => r.url === `${api}/buscar`).flush(pagina([]));
    fixture.detectChanges();
    expect(texto()).toContain('Todavía no hay evaluaciones cargadas');
  });

  it('permite reintentar si la búsqueda falla', async () => {
    await iniciar();
    http
      .expectOne((r) => r.url === `${api}/buscar`)
      .flush(
        { error: 'MongoDB no disponible' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    fixture.detectChanges();
    expect(texto()).toContain('No se pudo realizar la búsqueda');

    (fixture.nativeElement.querySelector('.estado button') as HTMLButtonElement).click();
    http.expectOne((r) => r.url === `${api}/buscar`).flush(pagina([evaluacion]));
    fixture.detectChanges();
    expect(texto()).toContain('asp1');
  });
});
