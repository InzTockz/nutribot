import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { API_URL } from './config';
import { AuthService } from './auth.service';

/**
 * Sprint 4: agrega "Authorization: Bearer <token>" SOLO a las peticiones privadas del panel.
 * El token nunca se envia a la API publica ni a otros sitios.
 * Si el backend responde 401 (token vencido o invalido), cierra la sesion y vuelve al login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const esPrivada = req.url.startsWith(`${API_URL}/api/admin`) || req.url.startsWith(`${API_URL}/api/auth/me`);
  const token = esPrivada ? auth.token : null;
  const peticion = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(peticion).pipe(
    catchError((err: HttpErrorResponse) => {
      if (esPrivada && err.status === 401) {
        auth.logout(true);
      }
      return throwError(() => err);
    })
  );
};
