import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';

/**
 * Muestra cualquier error HTTP al usuario sin que la aplicación se detenga.
 * El backend responde con { error, detalle, timestamp }.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const mensaje =
        err.status === 0
          ? 'No se pudo conectar con el servidor'
          : (err.error?.error ?? `Error ${err.status}: ${err.statusText}`);
      snackBar.open(mensaje, 'Cerrar', { duration: 6000 });
      return throwError(() => err);
    }),
  );
};
