import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { API_URL } from './config';
import { Rol, Sesion } from './modelos';

const CLAVE = 'nutribot.sesion';

/**
 * Sprint 4 (enabler de seguridad): sesion del personal.
 *
 * El token se guarda en sessionStorage: se borra al cerrar la pestana (menos exposicion que
 * localStorage). Ojo: ocultar menus en el frontend es solo comodidad; quien protege de verdad
 * los datos es el backend, que rechaza con 401/403 cualquier peticion sin el permiso necesario.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private sesion: Sesion | null = leer();

  constructor(private http: HttpClient, private router: Router) {}

  login(username: string, password: string): Observable<Sesion> {
    return this.http.post<Sesion>(`${API_URL}/api/auth/login`, { username, password }).pipe(
      tap((s) => {
        this.sesion = { ...s, expiraEn: Date.now() + s.expiraEnSegundos * 1000 };
        try { sessionStorage.setItem(CLAVE, JSON.stringify(this.sesion)); } catch { /* sin almacenamiento */ }
      })
    );
  }

  /** Cierra la sesion. Si vencio, la pantalla de inicio lo explica. */
  logout(vencida = false): void {
    this.limpiar();
    this.router.navigate(['/login'], vencida ? { queryParams: { vencida: 1 } } : {});
  }

  get token(): string | null {
    if (this.sesion?.expiraEn && Date.now() >= this.sesion.expiraEn) {
      this.limpiar();
    }
    return this.sesion?.token ?? null;
  }

  get estaAutenticado(): boolean {
    return this.token !== null;
  }

  get nombre(): string {
    return this.sesion?.nombre || this.sesion?.username || '';
  }

  get rol(): Rol | null {
    return this.estaAutenticado ? this.sesion!.rol : null;
  }

  tieneRol(...roles: Rol[]): boolean {
    const rol = this.rol;
    return rol !== null && roles.includes(rol);
  }

  /** Pantalla de inicio segun el rol: el asesor va directo a su trabajo del dia. */
  rutaInicio(): string {
    return this.rol === 'ASESOR' ? '/admin/consultas' : '/admin/dashboard';
  }

  private limpiar(): void {
    this.sesion = null;
    try { sessionStorage.removeItem(CLAVE); } catch { /* sin almacenamiento */ }
  }
}

function leer(): Sesion | null {
  try {
    const texto = sessionStorage.getItem(CLAVE);
    return texto ? (JSON.parse(texto) as Sesion) : null;
  } catch {
    return null;
  }
}
