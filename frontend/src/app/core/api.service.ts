import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/**
 * Punto único de comunicación con el backend. El frontend nunca habla
 * directamente con MongoDB ni con Neo4j.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  get<T>(ruta: string, params?: Record<string, string | number | undefined>): Observable<T> {
    return this.http.get<T>(`${this.baseUrl}/${ruta}`, { params: this.limpiarParams(params) });
  }

  /** Envía los archivos en el campo "archivos" y emite el progreso de la subida. */
  subirArchivos<T>(ruta: string, archivos: File[]): Observable<HttpEvent<T>> {
    const form = new FormData();
    archivos.forEach((archivo) => form.append('archivos', archivo, archivo.name));
    return this.http.post<T>(`${this.baseUrl}/${ruta}`, form, {
      reportProgress: true,
      observe: 'events',
    });
  }

  // Quita los filtros vacíos para no enviarlos como parámetros
  private limpiarParams(params?: Record<string, string | number | undefined>): HttpParams {
    let httpParams = new HttpParams();
    Object.entries(params ?? {}).forEach(([clave, valor]) => {
      if (valor !== undefined && valor !== null && `${valor}`.trim() !== '') {
        httpParams = httpParams.set(clave, `${valor}`);
      }
    });
    return httpParams;
  }
}
