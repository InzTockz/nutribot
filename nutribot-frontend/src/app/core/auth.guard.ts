import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Rol } from './modelos';

/** Sprint 4: el panel requiere iniciar sesion; si no, lleva al login y luego regresa a donde iba. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.estaAutenticado
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { volver: state.url } });
};

/** Sprint 4: cada pantalla declara sus roles en data.roles; sin el rol, lleva a la pantalla de inicio del usuario. */
export const rolGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const roles = (route.data?.['roles'] as Rol[] | undefined) ?? [];
  return roles.length === 0 || auth.tieneRol(...roles)
    ? true
    : inject(Router).createUrlTree([auth.rutaInicio()]);
};
