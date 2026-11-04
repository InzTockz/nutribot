import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { API_URL } from './config';

/** Pantalla vacia para que logout() pueda navegar a /login durante la prueba. */
@Component({ template: '' })
class Vacio {}

/** Sprint 4 (enabler de seguridad): sesion del personal en el navegador. */
describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: 'login', component: Vacio }])]
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function iniciar(rol: 'ADMIN' | 'ASESOR', segundos = 28800): void {
    auth.login('usuario', 'clave').subscribe();
    http.expectOne(`${API_URL}/api/auth/login`).flush({ token: 't', username: 'usuario', nombre: 'Usuario', rol, expiraEnSegundos: segundos });
  }

  it('sin sesion no esta autenticado', () => {
    expect(auth.estaAutenticado).toBeFalse();
    expect(auth.rol).toBeNull();
  });

  it('el ADMIN inicia en el dashboard y el ASESOR en consultas pendientes', () => {
    iniciar('ADMIN');
    expect(auth.rutaInicio()).toBe('/admin/dashboard');
    iniciar('ASESOR');
    expect(auth.rutaInicio()).toBe('/admin/consultas');
    expect(auth.tieneRol('ADMIN')).toBeFalse();
  });

  it('una sesion vencida deja de valer', () => {
    iniciar('ADMIN', -1);
    expect(auth.token).toBeNull();
    expect(auth.estaAutenticado).toBeFalse();
  });

  it('cerrar sesion borra el token del navegador', () => {
    iniciar('ADMIN');
    auth.logout();
    expect(auth.estaAutenticado).toBeFalse();
    expect(sessionStorage.getItem('nutribot.sesion')).toBeNull();
  });
});
