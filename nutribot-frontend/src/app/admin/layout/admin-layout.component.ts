import { Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { Rol } from '../../core/modelos';

interface Opcion { ruta: string; texto: string; icono: string; subtitulo: string; roles: Rol[]; }

/**
 * Sprint 4 — VISTA ADMINISTRADOR: marco del panel (menu lateral, barra superior y cerrar sesion).
 * El menu muestra solo las opciones del rol del usuario. Las paginas se dibujan en <router-outlet>.
 */
@Component({
  selector: 'app-admin-layout',
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.css'
})
export class AdminLayoutComponent implements OnDestroy {

  private readonly todas: Opcion[] = [
    { ruta: 'dashboard', texto: 'Dashboard', icono: '▦', subtitulo: 'Resumen general', roles: ['ADMIN'] },
    { ruta: 'base-conocimiento', texto: 'Base de conocimiento', icono: '📚', subtitulo: 'Administración de la base de conocimiento (RAG)', roles: ['ADMIN'] },
    { ruta: 'consultas', texto: 'Consultas pendientes', icono: '🗂', subtitulo: 'Bitácora de consultas pendientes', roles: ['ADMIN', 'ASESOR'] },
    { ruta: 'derivacion', texto: 'Clasificación y derivación', icono: '🔀', subtitulo: 'Clasificación y derivación automática', roles: ['ADMIN', 'ASESOR'] },
    { ruta: 'metricas', texto: 'Métricas', icono: '📈', subtitulo: 'Dashboard de métricas e indicadores', roles: ['ADMIN'] }
  ];

  readonly opciones: Opcion[];
  subtitulo = '';
  private sub: Subscription;

  constructor(public auth: AuthService, private router: Router) {
    this.opciones = this.todas.filter((o) => o.roles.some((r) => auth.tieneRol(r)));
    this.actualizarSubtitulo(this.router.url);
    this.sub = this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.actualizarSubtitulo(e.urlAfterRedirects));
  }

  get iniciales(): string {
    return this.auth.nombre.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('') || 'NB';
  }

  get rolTexto(): string {
    return this.auth.rol === 'ADMIN' ? 'Administrador' : 'Asesor de ventas';
  }

  salir(): void {
    this.auth.logout();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  private actualizarSubtitulo(url: string): void {
    this.subtitulo = this.todas.find((o) => url.includes(`/admin/${o.ruta}`))?.subtitulo ?? '';
  }
}
