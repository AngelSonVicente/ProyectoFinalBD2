import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { ResumenCarga } from '../../core/modelos';
import { Carga } from './carga';

describe('Carga', () => {
  let fixture: ComponentFixture<Carga>;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/cargas`;

  const elegir = (...archivos: File[]) => {
    const entrada = fixture.nativeElement.querySelector('input[type=file]') as HTMLInputElement;
    Object.defineProperty(entrada, 'files', { value: archivos, configurable: true });
    entrada.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Carga],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Carga);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(url).flush([]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('muestra el historial vacío', () => {
    expect(fixture.nativeElement.textContent).toContain('Todavía no se ha cargado ningún archivo');
  });

  it('solo acepta archivos CSV y no repite el mismo archivo', () => {
    const csv = new File(['a,b'], 'datos.csv', { type: 'text/csv' });
    elegir(csv, new File(['x'], 'foto.png'));
    elegir(csv);

    const nombres = Array.from(fixture.nativeElement.querySelectorAll('.lista .nombre')).map(
      (e) => (e as HTMLElement).textContent,
    );
    expect(nombres).toEqual(['datos.csv']);
  });

  it('sube los archivos y muestra el resultado de cada uno', () => {
    elegir(new File(['a,b'], 'datos.csv'));
    (fixture.nativeElement.querySelector('.acciones button') as HTMLButtonElement).click();

    const solicitud = http.expectOne(url);
    expect(solicitud.request.method).toBe('POST');
    expect((solicitud.request.body as FormData).getAll('archivos').length).toBe(1);

    const resumen: ResumenCarga = {
      id: 'abc',
      nombreArchivo: 'datos.csv',
      leidos: 10,
      procesados: 8,
      rechazados: 2,
      duplicados: 0,
      tiempoMs: 1500,
      estado: 'COMPLETADO_CON_RECHAZOS',
      mensaje: null,
    };
    solicitud.flush([resumen]);
    http.expectOne(url).flush([]);
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Resultado de la carga');
    expect(texto).toContain('Con rechazos');
    expect(texto).toContain('1.5 s');
    expect(fixture.nativeElement.querySelectorAll('.lista li').length).toBe(0);
  });
});
