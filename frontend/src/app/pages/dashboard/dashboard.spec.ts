import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { ResultadoConsulta } from '../../core/modelos';
import { SECCIONES } from './consultas';
import { agrupadas, abreviar } from './graficas';
import { formatearCelda } from './tabla-datos/tabla-datos';
import { TarjetaConsulta } from './tarjeta-consulta/tarjeta-consulta';

const resultado = (datos: ResultadoConsulta['datos']): ResultadoConsulta => ({
  id: 8,
  titulo: 'Distribución de aprobados',
  descripcion: 'Descripción',
  coleccion: 'evaluaciones',
  total: datos.length,
  tiempoMs: 12,
  datos,
});

describe('Dashboard', () => {
  it('incluye las 20 consultas una sola vez (Q11 y Q15 van en la pestaña por aspirante)', () => {
    const ids = SECCIONES.flatMap((s) => s.consultas.map((c) => c.id)).concat(11, 15);
    expect(new Set(ids).size).toBe(20);
    expect([...ids].sort((a, b) => a - b)).toEqual(Array.from({ length: 20 }, (_, i) => i + 1));
  });

  it('formatea los valores de MongoDB para la tabla', () => {
    expect(formatearCelda(true)).toBe('Sí');
    expect(formatearCelda(null)).toBe('—');
    expect(formatearCelda(2021, 'anio_ingreso')).toBe('2021');
    expect(formatearCelda(['FISICA', 'QUIMICA'])).toBe('FISICA, QUIMICA');
    // Fecha guardada a medianoche UTC: no debe correrse al día anterior
    expect(formatearCelda('2020-06-17T00:00:00.000Z')).toContain('17');
  });

  it('agrupa en OTRAS las series que no alcanzan color', () => {
    // Valores 10..1: quedan las siete mayores y OTRAS suma 3 + 2 + 1
    const datos = Array.from({ length: 10 }, (_, i) => ({ x: 'A', serie: `S${i}`, valor: 10 - i }));
    const opciones = agrupadas(datos, { categoria: 'x', serie: 'serie', valor: 'valor' });
    const series = opciones.series as { name: string; data: number[] }[];
    expect(series.length).toBe(8);
    expect(series.at(-1)).toEqual(expect.objectContaining({ name: 'OTRAS', data: [6] }));
  });

  it('acorta los nombres de carrera en los ejes', () => {
    expect(abreviar('INGENIERIA EN CIENCIAS Y SISTEMAS')).toBe('ING. CIENCIAS Y SISTEMAS');
  });
});

describe('TarjetaConsulta', () => {
  let http: HttpTestingController;

  const crear = () => {
    const fixture = TestBed.createComponent(TarjetaConsulta);
    fixture.componentRef.setInput('config', { id: 8 });
    fixture.detectChanges();
    return fixture;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TarjetaConsulta],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('muestra como tabla una consulta sin gráfica', () => {
    const fixture = crear();
    http
      .expectOne(`${environment.apiUrl}/mongo/8`)
      .flush(resultado([{ municipio: 'COBAN', aspirantes_aprobados: 590 }]));
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Distribución de aprobados');
    expect(texto).toContain('COBAN');
    expect(texto).toContain('590');
    expect(texto).toContain('1 fila');
  });

  it('avisa cuando no hay resultados', () => {
    const fixture = crear();
    http.expectOne(`${environment.apiUrl}/mongo/8`).flush(resultado([]));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('no devolvió resultados');
  });

  it('permite reintentar si la consulta falla', () => {
    const fixture = crear();
    http
      .expectOne(`${environment.apiUrl}/mongo/8`)
      .flush(
        { error: 'MongoDB no disponible' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    fixture.detectChanges();

    const boton = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(boton.textContent).toContain('Reintentar');
    boton.click();
    http.expectOne(`${environment.apiUrl}/mongo/8`).flush(resultado([{ municipio: 'COBAN' }]));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('COBAN');
  });
});
