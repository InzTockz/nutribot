import { Route } from '@angular/router';
import { routes } from './app.routes';
import { authGuard, rolGuard } from './core/auth.guard';

/**
 * Sprint 4: la matriz de acceso del frontend coincide con la del backend (SecurityConfig).
 * Recordatorio: esto es comodidad de navegacion; la proteccion real la hace el backend.
 */
describe('Rutas (dos perspectivas)', () => {
  const admin = routes.find((r) => r.path === 'admin') as Route;
  const hija = (path: string) => admin.children!.find((r) => r.path === path) as Route;

  it('el chat del cliente es publico', () => {
    const chat = routes.find((r) => r.path === 'chat') as Route;
    expect(chat.canActivate).toBeUndefined();
  });

  it('el panel exige iniciar sesion', () => {
    expect(admin.canActivate).toContain(authGuard);
  });

  it('el ADMIN es el unico rol de dashboard, base de conocimiento y metricas', () => {
    for (const p of ['dashboard', 'base-conocimiento', 'metricas']) {
      expect(hija(p).canActivate).toContain(rolGuard);
      expect(hija(p).data?.['roles']).toEqual(['ADMIN']);
    }
  });

  it('el ASESOR entra a consultas pendientes y a derivaciones', () => {
    for (const p of ['consultas', 'derivacion']) {
      expect(hija(p).data?.['roles']).toEqual(['ADMIN', 'ASESOR']);
    }
  });

  it('cualquier otra direccion lleva al chat', () => {
    expect(routes.find((r) => r.path === '**')?.redirectTo).toBe('chat');
  });
});
