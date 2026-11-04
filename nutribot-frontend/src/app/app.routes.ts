import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { authGuard, rolGuard } from './core/auth.guard';
import { AuthService } from './core/auth.service';
import { ChatComponent } from './cliente/chat/chat.component';

/**
 * Sprint 4: mapa de rutas con dos perspectivas.
 *  - Cliente:        /chat (publica). Es lo unico que se publica en la web de Battilana.
 *  - Administrador:  /login y /admin/** (requiere sesion; cada pantalla declara sus roles).
 * El panel se carga bajo demanda (loadComponent): el navegador del cliente no descarga su codigo.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'chat' },
  { path: 'chat', title: 'NutriBot · Asistente de Battilana', component: ChatComponent },
  {
    path: 'login',
    title: 'NutriBot · Iniciar sesión',
    loadComponent: () => import('./login/login.component').then((m) => m.LoginComponent)
  },
  {
    path: 'admin',
    canActivate: [authGuard],
    loadComponent: () => import('./admin/layout/admin-layout.component').then((m) => m.AdminLayoutComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: () => inject(AuthService).rutaInicio() },
      {
        path: 'dashboard', title: 'NutriBot · Dashboard',
        canActivate: [rolGuard], data: { roles: ['ADMIN'] },
        loadComponent: () => import('./admin/dashboard/dashboard.component').then((m) => m.DashboardComponent)
      },
      {
        path: 'base-conocimiento', title: 'NutriBot · Base de conocimiento',
        canActivate: [rolGuard], data: { roles: ['ADMIN'] },
        loadComponent: () => import('./admin/base-conocimiento/base-conocimiento.component').then((m) => m.BaseConocimientoComponent)
      },
      {
        path: 'consultas', title: 'NutriBot · Consultas pendientes',
        canActivate: [rolGuard], data: { roles: ['ADMIN', 'ASESOR'] },
        loadComponent: () => import('./admin/consultas/consultas.component').then((m) => m.ConsultasComponent)
      },
      {
        path: 'derivacion', title: 'NutriBot · Clasificación y derivación',
        canActivate: [rolGuard], data: { roles: ['ADMIN', 'ASESOR'] },
        loadComponent: () => import('./admin/derivacion/derivacion.component').then((m) => m.DerivacionComponent)
      },
      {
        path: 'metricas', title: 'NutriBot · Métricas',
        canActivate: [rolGuard], data: { roles: ['ADMIN'] },
        loadComponent: () => import('./admin/metricas/metricas.component').then((m) => m.MetricasComponent)
      }
    ]
  },
  { path: '**', redirectTo: 'chat' }
];
